/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen

import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.codegen.context.ClassContext
import org.jetbrains.kotlin.codegen.context.CodegenContext
import org.jetbrains.kotlin.codegen.context.MethodContext
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin.Companion.NO_ORIGIN
import org.jetbrains.kotlin.resolve.jvm.diagnostics.OtherOrigin
import org.jetbrains.org.objectweb.asm.Opcodes.*
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

class CodeFragmentCodegenInfo(
    val classDescriptor: ClassDescriptor,
    val methodDescriptor: FunctionDescriptor,
    val parameters: List<IParameter>,
    val referenceInterceptor: ExpressionCodegenExtension.Context.(KtExpression, ResolvedCall<*>?) -> StackValue?
) {
    val classType: Type = Type.getObjectType(classDescriptor.name.asString())

    interface IParameter {
        val descriptor: DeclarationDescriptor
    }
}

class CodeFragmentCodegen private constructor(
    private val codeFragment: KtExpressionCodeFragment,
    private val info: CodeFragmentCodegenInfo,
    private val classContext: ClassContext,
    state: GenerationState,
    builder: ClassBuilder
) : MemberCodegen<KtCodeFragment>(state, null, classContext, codeFragment, builder) {
    private val methodDescriptor = info.methodDescriptor

    override fun generateDeclaration() {
        v.defineClass(
            codeFragment,
            state.classFileVersion,
            ACC_PUBLIC or ACC_SUPER,
            info.classType.internalName,
            null,
            "java/lang/Object",
            emptyArray()
        )
    }

    override fun generateBody() {
        genConstructor()
        genMethod(classContext.intoFunction(methodDescriptor))
    }

    override fun generateKotlinMetadataAnnotation() {
        writeSyntheticClassMetadata(v, state)
    }

    private fun genConstructor() {
        val mv = v.newMethod(NO_ORIGIN, ACC_PUBLIC, "<init>", "()V", null, null)

        if (state.classBuilderMode.generateBodies) {
            mv.visitCode()

            val iv = InstructionAdapter(mv)
            iv.load(0, info.classType)
            iv.invokespecial("java/lang/Object", "<init>", "()V", false)
            iv.areturn(Type.VOID_TYPE)
        }

        mv.visitMaxs(-1, -1)
        mv.visitEnd()
    }

    private fun genMethod(methodContext: MethodContext) {
        val methodSignature = typeMapper.mapSignatureSkipGeneric(methodContext.functionDescriptor)
        require(info.parameters.size == methodSignature.valueParameters.size)

        val returnType = methodSignature.returnType
        val parameterTypes = methodSignature.valueParameters.map { it.asmType }
        val parametersWithTypes = info.parameters.zip(parameterTypes)

        val methodDesc = Type.getMethodDescriptor(returnType, *parameterTypes.toTypedArray())

        val mv = v.newMethod(
            OtherOrigin(codeFragment, methodContext.functionDescriptor),
            ACC_PUBLIC or ACC_STATIC,
            methodDescriptor.name.asString(), methodDesc,
            null, null
        )

        if (state.classBuilderMode.generateBodies) {
            mv.visitCode()

            val frameMap = FrameMap()
            parametersWithTypes.forEach { (parameter, asmType) -> frameMap.enter(parameter.descriptor, asmType) }

            val codegen = object : ExpressionCodegen(mv, frameMap, returnType, methodContext, state, this) {
                override fun visitNonIntrinsicSimpleNameExpression(
                    expression: KtSimpleNameExpression,
                    receiver: StackValue,
                    descriptor: DeclarationDescriptor,
                    resolvedCall: ResolvedCall<*>?,
                    isSyntheticField: Boolean
                ): StackValue {
                    intercept(expression, resolvedCall)?.let { return it }
                    return super.visitNonIntrinsicSimpleNameExpression(expression, receiver, descriptor, resolvedCall, isSyntheticField)
                }

                override fun visitThisExpression(expression: KtThisExpression, receiver: StackValue?): StackValue {
                    intercept(expression)?.let { return it }
                    return super.visitThisExpression(expression, receiver)
                }

                override fun visitSuperExpression(expression: KtSuperExpression, data: StackValue?): StackValue {
                    intercept(expression)?.let { return it }
                    return super.visitSuperExpression(expression, data)
                }

                private fun intercept(expression: KtExpression, resolvedCall: ResolvedCall<*>? = null): StackValue? {
                    val context = ExpressionCodegenExtension.Context(this, typeMapper, v)
                    return info.referenceInterceptor(context, expression, resolvedCall)
                }
            }
            codegen.gen(codeFragment.getContentElement(), returnType)

            val iv = InstructionAdapter(mv)
            iv.areturn(returnType)

            parametersWithTypes.forEach { (parameter, _) -> frameMap.leave(parameter.descriptor) }
        }

        mv.visitMaxs(-1, -1)
        mv.visitEnd()
    }

    companion object {
        val INFO_USERDATA_KEY = Key.create<CodeFragmentCodegenInfo>("CODE_FRAGMENT_CODEGEN_INFO")

        @JvmStatic
        fun createCodegen(
            declaration: KtExpressionCodeFragment,
            state: GenerationState,
            parentContext: CodegenContext<*>
        ): CodeFragmentCodegen {
            val info = declaration.getUserData(INFO_USERDATA_KEY) ?: error("Codegen info user data is not set")
            val classDescriptor = info.classDescriptor
            val builder = state.factory.newVisitor(OtherOrigin(declaration, classDescriptor), info.classType, declaration.containingFile)
            val classContext = parentContext.intoClass(classDescriptor, OwnerKind.IMPLEMENTATION, state)
            return CodeFragmentCodegen(declaration, info, classContext, state, builder)
        }
    }
}
