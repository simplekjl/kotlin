/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.expression.*
import com.intellij.diagnostic.LogMessageEx
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ExceptionUtil
import com.sun.jdi.*
import com.sun.jdi.Value
import com.sun.jdi.request.EventRequest
import org.jetbrains.eval4j.*
import org.jetbrains.eval4j.Value as Eval4JValue
import org.jetbrains.eval4j.jdi.JDIEval
import org.jetbrains.eval4j.jdi.asJdiValue
import org.jetbrains.eval4j.jdi.asValue
import org.jetbrains.eval4j.jdi.makeInitialFrame
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches.*
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches.Companion.compileCodeFragmentCacheAware
import org.jetbrains.kotlin.idea.debugger.evaluate.compilingEvaluator.loadClassesSafely
import org.jetbrains.kotlin.idea.runInReadActionWithWriteActionPriorityWithPCE
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.attachment.attachmentByPsiFile
import org.jetbrains.kotlin.idea.util.attachment.mergeAttachments
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.AnalyzingUtils
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.isInlineClassType
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.org.objectweb.asm.*
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import java.util.*

internal val LOG = Logger.getInstance("#org.jetbrains.kotlin.idea.debugger.evaluate.KotlinEvaluator")
internal const val GENERATED_FUNCTION_NAME = "generated_for_debugger_fun"
internal const val GENERATED_CLASS_NAME = "Generated_for_debugger_class"

object KotlinEvaluationBuilder : EvaluatorBuilder {
    override fun build(codeFragment: PsiElement, position: SourcePosition?): ExpressionEvaluator {
        if (codeFragment !is KtCodeFragment || position == null) {
            return EvaluatorBuilderImpl.getInstance()!!.build(codeFragment, position)
        }

        if (position.line < 0) {
            throw EvaluateExceptionUtil.createEvaluateException("Couldn't evaluate kotlin expression at $position")
        }

        val file = position.file
        if (file is KtFile) {
            val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
            if (document == null || document.lineCount < position.line) {
                throw EvaluateExceptionUtil.createEvaluateException(
                    "Couldn't evaluate kotlin expression: breakpoint is placed outside the file. " +
                            "It may happen when you've changed source file after starting a debug process."
                )
            }
        }

        if (codeFragment.context !is KtElement) {
            val attachments = arrayOf(
                attachmentByPsiFile(position.file),
                attachmentByPsiFile(codeFragment),
                Attachment("breakpoint.info", "line: ${position.line}")
            )

            LOG.error(
                "Trying to evaluate ${codeFragment::class.java} with context ${codeFragment.context?.javaClass}",
                mergeAttachments(*attachments)
            )
            throw EvaluateExceptionUtil.createEvaluateException("Couldn't evaluate kotlin expression in this context")
        }

        return ExpressionEvaluatorImpl(KotlinEvaluator(codeFragment, position))
    }
}

class KotlinEvaluator(val codeFragment: KtCodeFragment, val sourcePosition: SourcePosition) : Evaluator {
    override fun evaluate(context: EvaluationContextImpl): Any? {
        if (codeFragment.text.isEmpty()) {
            return context.debugProcess.virtualMachineProxy.mirrorOfVoid()
        }

        if (DumbService.getInstance(codeFragment.project).isDumb) {
            throw EvaluateExceptionUtil.createEvaluateException("Code fragment evaluation is not available in the dumb mode")
        }

        try {
            return evaluateSafe(context)
        } catch (e: EvaluateException) {
            throw e
        } catch (e: ProcessCanceledException) {
            exception(e)
        } catch (e: Eval4JInterpretingException) {
            exception(e.cause)
        } catch (e: Exception) {
            val isSpecialException = isSpecialException(e)
            if (isSpecialException) {
                exception(e)
            }

            val text = runReadAction { codeFragment.context?.text ?: "null" }
            val attachments = arrayOf(
                attachmentByPsiFile(sourcePosition.file),
                attachmentByPsiFile(codeFragment),
                Attachment("breakpoint.info", "line: ${runReadAction { sourcePosition.line }}"),
                Attachment("context.info", text)
            )

            LOG.error(
                LogMessageEx.createEvent(
                    "Couldn't evaluate expression",
                    ExceptionUtil.getThrowableText(e),
                    mergeAttachments(*attachments)
                )
            )

            val cause = if (e.message != null) ": ${e.message}" else ""
            exception("An exception occurs during Evaluate Expression Action $cause")
        }
    }

    private fun evaluateSafe(context: EvaluationContextImpl): Any? {
        val (compiledData, isCompiledDataFromCache) = compileCodeFragmentCacheAware(codeFragment, sourcePosition, ::compileCodeFragment)
        val classLoaderRef = loadClassesSafely(context, compiledData.compilationResult.classes)

        val result = if (classLoaderRef != null) {
            evaluateWithCompilation(context, compiledData, classLoaderRef) ?: evaluateWithEval4J(context, compiledData, classLoaderRef)
        } else {
            evaluateWithEval4J(context, compiledData, classLoaderRef)
        }

        // If bytecode was taken from cache and exception was thrown - recompile bytecode and run eval4j again
        if (isCompiledDataFromCache && result is ExceptionThrown && result.kind == ExceptionThrown.ExceptionKind.BROKEN_CODE) {
            val (recompiledData, _) = compileCodeFragmentCacheAware(codeFragment, sourcePosition, ::compileCodeFragment, force = true)
            return evaluateWithEval4J(context, recompiledData, classLoaderRef).toJdiValue(context)
        }

        return when (result) {
            is InterpreterResult -> result.toJdiValue(context)
            else -> result
        }
    }

    private fun compileCodeFragment(): CompiledDataDescriptor {
        var analysisResult = codeFragment.checkForErrors()

        if (codeFragment.wrapToStringIfNeeded(analysisResult.bindingContext)) {
            // Repeat analysis with toString() added
            analysisResult = codeFragment.checkForErrors()
        }

        val bindingContext = analysisResult.bindingContext
        val moduleDescriptor = analysisResult.moduleDescriptor

        val result = CodeFragmentCompiler.compile(codeFragment, bindingContext, moduleDescriptor)
        return CompiledDataDescriptor(result, sourcePosition, emptyList())
    }

    private fun KtCodeFragment.wrapToStringIfNeeded(bindingContext: BindingContext): Boolean {
        if (this !is KtExpressionCodeFragment) {
            return false
        }

        val contentElement = runReadAction { getContentElement() }
        val expressionType = bindingContext[BindingContext.EXPRESSION_TYPE_INFO, contentElement]?.type
        if (contentElement != null && expressionType?.isInlineClassType() == true) {
            val newExpression = runReadAction {
                val expressionText = contentElement.text
                KtPsiFactory(project).createExpression("($expressionText).toString()")
            }
            runInEdtAndWait {
                project.executeWriteCommand("Wrap with 'toString()'") {
                    contentElement.replace(newExpression)
                }
            }
            return true
        }

        return false
    }

    private data class ErrorCheckingResult(
        val bindingContext: BindingContext,
        val moduleDescriptor: ModuleDescriptor,
        val files: List<KtFile>
    )

    private fun KtFile.checkForErrors(): ErrorCheckingResult {
        return runInReadActionWithWriteActionPriorityWithPCE {
            try {
                AnalyzingUtils.checkForSyntacticErrors(this)
            } catch (e: IllegalArgumentException) {
                throw EvaluateExceptionUtil.createEvaluateException(e.message)
            }

            val filesToAnalyze = listOf(this)
            val resolutionFacade = KotlinCacheService.getInstance(project).getResolutionFacade(filesToAnalyze)
            val analysisResult = resolutionFacade.analyzeWithAllCompilerChecks(filesToAnalyze)

            if (analysisResult.isError()) {
                exception(analysisResult.error)
            }

            val bindingContext = analysisResult.bindingContext
            val filteredDiagnostics = bindingContext.diagnostics.filter { it.factory !in IGNORED_DIAGNOSTICS }
            filteredDiagnostics.firstOrNull { it.severity == Severity.ERROR }?.let {
                if (it.psiElement.containingFile == this) {
                    exception(DefaultErrorMessages.render(it))
                }
            }

            ErrorCheckingResult(bindingContext, analysisResult.moduleDescriptor, Collections.singletonList(this))
        }
    }

    private fun evaluateWithCompilation(
        context: EvaluationContextImpl,
        compiledData: CompiledDataDescriptor,
        classLoader: ClassLoaderReference
    ): Value? {
        return try {
            runEvaluation(context, compiledData, classLoader) { eval, args, thread, invokePolicy ->
                val mainClassValue = eval.loadClass(Type.getObjectType(GENERATED_CLASS_NAME), classLoader) as? ObjectValue
                val mainClass = (mainClassValue?.value as? ClassObjectReference)?.reflectedType() as? ClassType
                    ?: error("Can not find class \"$GENERATED_CLASS_NAME\"")

                mainClass.invokeMethod(thread, mainClass.methods().single(), args, invokePolicy)
            }
        } catch (e: Throwable) {
            LOG.error("Unable to evaluate expression with compilation", e)
            return null
        }
    }

    private fun evaluateWithEval4J(
        context: EvaluationContextImpl,
        compiledData: CompiledDataDescriptor,
        classLoader: ClassLoaderReference?
    ): InterpreterResult {
        val mainClassBytecode = compiledData.mainClass.bytes
        val mainClassAsmNode = ClassNode().apply { ClassReader(mainClassBytecode).accept(this, 0) }
        val mainMethod = mainClassAsmNode.methods.first { it.name == GENERATED_FUNCTION_NAME }

        return runEvaluation(context, compiledData, classLoader) { eval, args, _, _ ->
            interpreterLoop(mainMethod, makeInitialFrame(mainMethod, args.map { it.asValue() }), eval)
        }
    }

    private fun <T> runEvaluation(
        context: EvaluationContextImpl,
        compiledData: CompiledDataDescriptor,
        classLoader: ClassLoaderReference?,
        block: (JDIEval, List<Value?>, ThreadReference, Int) -> T
    ): T {
        val vm = context.debugProcess.virtualMachineProxy.virtualMachine

        val thread = context.suspendContext.thread?.threadReference ?: error("Can not find a thread to run evaluation on")
        val invokePolicy = context.suspendContext.getInvokePolicy()
        val eval = JDIEval(vm, classLoader, thread, invokePolicy)

        // Preload additional classes
        compiledData.compilationResult.classes.asSequence()
            .filter { !it.isMainClass }
            .forEach { eval.loadClass(Type.getObjectType(it.className), classLoader) }

        return vm.executeWithBreakpointsDisabled {
            val args = calculateMainMethodCallArguments(context, compiledData)
            block(eval, args, thread, invokePolicy)
        }
    }

    private fun calculateMainMethodCallArguments(context: EvaluationContextImpl, compiledData: CompiledDataDescriptor): List<Value?> {
        val asmValueParameters = compiledData.compilationResult.mainMethodSignature.valueParameters.map { it.asmType }
        val valueParameters = compiledData.compilationResult.parameterInfo.parameters
        require(asmValueParameters.size == valueParameters.size)

        val args = valueParameters.zip(asmValueParameters)

        val variableFinder = VariableFinder.instance(context) ?: error("Frame map is not available")
        return args.map { (parameter, asmType) ->
            val value = variableFinder.find(parameter, asmType)

            if (value == null) {
                val name = parameter.rawString

                if (parameter in compiledData.compilationResult.parameterInfo.crossingBounds) {
                    throw EvaluateExceptionUtil.createEvaluateException("'$name' is not captured")
                } else {
                    throw VariableFinder.variableNotFound(context, buildString {
                        append("Cannot find local variable: name = '").append(name).append("', type = ").append(asmType.className)
                    })
                }
            }

            coerceBoxing(value.value, asmType)
        }
    }

    private fun coerceBoxing(value: Value?, type: Type): Value? {
        if (value == null) {
            return value
        }

        //TODO fix coercion
        return value
    }

    override fun getModifier() = null

    companion object {
        private fun exception(msg: String): Nothing = throw EvaluateExceptionUtil.createEvaluateException(msg)
        private fun exception(e: Throwable): Nothing = throw EvaluateExceptionUtil.createEvaluateException(e)

        private val IGNORED_DIAGNOSTICS: Set<DiagnosticFactory<*>> = Errors.INVISIBLE_REFERENCE_DIAGNOSTICS

        private fun InterpreterResult.toJdiValue(context: EvaluationContextImpl): com.sun.jdi.Value? {
            val jdiValue = when (this) {
                is ValueReturned -> result
                is ExceptionThrown -> {
                    when {
                        this.kind == ExceptionThrown.ExceptionKind.FROM_EVALUATED_CODE ->
                            exception(InvocationException(this.exception.value as ObjectReference))
                        this.kind == ExceptionThrown.ExceptionKind.BROKEN_CODE ->
                            throw exception.value as Throwable
                        else ->
                            exception(exception.toString())
                    }
                }
                is AbnormalTermination -> exception(message)
                else -> throw IllegalStateException("Unknown result value produced by eval4j")
            }

            val vm = context.debugProcess.virtualMachineProxy.virtualMachine

            val sharedVar = getValueIfSharedVar(jdiValue)
            return sharedVar?.value ?: jdiValue.asJdiValue(vm, jdiValue.asmType)
        }

        private fun getValueIfSharedVar(value: Eval4JValue): VariableFinder.Result? {
            val obj = value.obj(value.asmType) as? ObjectReference ?: return null
            return VariableFinder.Result(VariableFinder.unwrapRefValue(obj))
        }
    }
}

internal fun SuspendContext.getInvokePolicy(): Int {
    return if (suspendPolicy == EventRequest.SUSPEND_EVENT_THREAD) ObjectReference.INVOKE_SINGLE_THREADED else 0
}

fun Type.getClassDescriptor(scope: GlobalSearchScope): ClassDescriptor? {
    if (AsmUtil.isPrimitive(this)) return null

    val jvmName = JvmClassName.byInternalName(internalName).fqNameForClassNameWithoutDollars

    // TODO: use the correct built-ins from the module instead of DefaultBuiltIns here
    JavaToKotlinClassMap.mapJavaToKotlin(jvmName)?.let(
        DefaultBuiltIns.Instance.builtInsModule::findClassAcrossModuleDependencies
    )?.let { return it }

    return runReadAction {
        val classes = JavaPsiFacade.getInstance(scope.project).findClasses(jvmName.asString(), scope)
        if (classes.isEmpty()) null
        else {
            classes.first().getJavaClassDescriptor()
        }
    }
}

private fun <T> VirtualMachine.executeWithBreakpointsDisabled(block: () -> T): T {
    val allRequests = eventRequestManager().breakpointRequests() + eventRequestManager().classPrepareRequests()

    try {
        allRequests.forEach { it.disable() }
        return block()
    } finally {
        allRequests.forEach { it.enable() }
    }
}

private fun isSpecialException(th: Throwable): Boolean {
    return when (th) {
        is ClassNotPreparedException,
        is InternalException,
        is AbsentInformationException,
        is ClassNotLoadedException,
        is IncompatibleThreadStateException,
        is InconsistentDebugInfoException,
        is ObjectCollectedException,
        is VMDisconnectedException -> true
        else -> false
    }
}