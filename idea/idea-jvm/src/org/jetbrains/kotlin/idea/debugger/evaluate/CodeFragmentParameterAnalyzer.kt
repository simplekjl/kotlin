/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.codegen.AsmUtil.THIS
import org.jetbrains.kotlin.codegen.CodeFragmentCodegenInfo
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.debugger.evaluate.CodeFragmentParameterInfo.Parameter
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.load.java.sam.SingleAbstractMethodUtils
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.isDotReceiver
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType

class CodeFragmentParameterInfo(
    val parameters: List<Parameter>,
    val mappings: Map<PsiElement, Parameter>,
    val crossingBounds: Set<Parameter>
) {
    sealed class Parameter(
        val index: Int,
        val type: KotlinType,
        override val descriptor: DeclarationDescriptor,
        var rawString: String
    ) : CodeFragmentCodegenInfo.IParameter {
        class Ordinary(index: Int, type: KotlinType, descriptor: DeclarationDescriptor, val name: String) :
            Parameter(index, type, descriptor, name)

        @Suppress("ConvertToStringTemplate")
        class ExtensionThis(index: Int, type: KotlinType, descriptor: DeclarationDescriptor, val label: String) :
            Parameter(index, type, descriptor, THIS + "@" + label)

        class LocalFunction(
            index: Int, type: KotlinType, descriptor: DeclarationDescriptor,
            rawString: String, val name: String, var functionIndex: Int
        ) : Parameter(index, type, descriptor, rawString)
    }
}

class CodeFragmentParameterAnalyzer(private val bindingContext: BindingContext, private val codeFragment: KtCodeFragment) {
    private var used = false

    private val mappings = hashMapOf<PsiElement, Parameter>()
    private val parameters = LinkedHashMap<DeclarationDescriptor, Parameter>()
    private val crossingBounds = mutableSetOf<Parameter>()

    fun analyze(): CodeFragmentParameterInfo {
        checkUsedOnce()

        codeFragment.accept(object : KtTreeVisitor<Unit>() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, data: Unit?): Void? {
                if (runReadAction { !expression.isDotReceiver() }) {
                    process(expression, expression, ::processSimpleNameExpression)
                }
                return null
            }

            override fun visitThisExpression(expression: KtThisExpression, data: Unit?): Void? {
                val instanceReference = runReadAction { expression.instanceReference }
                process(expression, instanceReference, ::processThisExpression)
                return null
            }
        }, Unit)

        return CodeFragmentParameterInfo(parameters.values.toList(), mappings, crossingBounds)
    }

    private fun <T> process(
        element: T,
        reference: KtReferenceExpression,
        block: (T, DeclarationDescriptor, PsiElement) -> Unit
    ) {
        val target = bindingContext[BindingContext.REFERENCE_TARGET, reference]
        if (target is DeclarationDescriptorWithVisibility && target.visibility == Visibilities.LOCAL) {
            val declarationPsiElement = target.toSourceElement.getPsi()?.takeIf { !codeFragment.isAncestorSafe(it) }
            if (declarationPsiElement != null) {
                runReadAction {
                    block(element, target, declarationPsiElement)
                }
            }
        }
    }

    private fun PsiElement.isAncestorSafe(element: PsiElement) = runReadAction { this.isAncestor(element, true) }

    private fun processSimpleNameExpression(expression: KtSimpleNameExpression, target: DeclarationDescriptor, targetPsi: PsiElement) {
        val parameter = when (target) {
            is FunctionDescriptor -> {
                val type = SingleAbstractMethodUtils.getFunctionTypeForAbstractMethod(target, false)
                parameters.getOrPut(target) {
                    Parameter.Ordinary(parameters.size, type, target, target.name.asString())
                }
            }
            is ValueDescriptor -> {
                parameters.getOrPut(target) {
                    Parameter.Ordinary(parameters.size, target.type, target, target.name.asString())
                }
            }
            else -> return
        }

        mappings[expression] = parameter

        if (doesCrossInlineBounds(expression, targetPsi)) {
            crossingBounds += parameter
        }
    }

    private fun processThisExpression(expression: KtThisExpression, target: DeclarationDescriptor, targetPsi: PsiElement) {
        if (target is FunctionDescriptor) {
            val receiverParameter = target.extensionReceiverParameter
            if (receiverParameter != null) {
                val name = getThisLabelName(expression, target, targetPsi).asString()
                val parameter = parameters.getOrPut(target) {
                    Parameter.ExtensionThis(parameters.size, receiverParameter.type, target, name)
                }
                mappings[expression] = parameter
            }
        }
    }

    private fun getThisLabelName(expression: KtThisExpression, target: DeclarationDescriptor, targetPsi: PsiElement): Name {
        expression.getLabelNameAsName()?.let { return it }

        if (targetPsi is KtFunctionLiteral) {
            val labeledExpression = (targetPsi.parent as? KtLambdaExpression)?.parent as? KtLabeledExpression
            val labelName = labeledExpression?.getLabelNameAsName()
            if (labelName != null) {
                return labelName
            }
        }

        return target.name
    }

    private fun doesCrossInlineBounds(expression: KtSimpleNameExpression, declaration: PsiElement): Boolean {
        val declarationParent = declaration.parent ?: return false
        var currentParent: PsiElement? = expression.parent?.takeIf { it.isInside(declarationParent) } ?: return false

        while (currentParent != null && currentParent != declarationParent) {
            if (currentParent is KtFunctionLiteral) {
                val functionDescriptor = bindingContext[BindingContext.FUNCTION, currentParent]
                if (functionDescriptor != null && !functionDescriptor.isInline) {
                    return true
                }
            }

            currentParent = when (currentParent) {
                is KtCodeFragment -> currentParent.context
                else -> currentParent.parent
            }
        }

        return false
    }

    private tailrec fun PsiElement.isInside(parent: PsiElement): Boolean {
        if (parent.isAncestor(this)) {
            return true
        }

        val context = (this.containingFile as? KtCodeFragment)?.context ?: return false
        return context.isInside(parent)
    }

    private fun checkUsedOnce() {
        if (used) {
            error(CodeFragmentParameterAnalyzer::class.java.simpleName + " may be only used once")
        }

        used = true
    }
}