/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.debugger.evaluate.CodeFragmentParameterInfo.Parameter
import org.jetbrains.kotlin.idea.util.application.runReadAction
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
    data class Parameter(val index: Int, val name: Name, val type: KotlinType)
}

class CodeFragmentParameterAnalyzer(private val bindingContext: BindingContext) {
    private var used = false

    private val mappings = hashMapOf<PsiElement, Parameter>()
    private val parameters = LinkedHashMap<DeclarationDescriptor, Parameter>()
    private val crossingBounds = mutableSetOf<Parameter>()

    fun analyze(codeFragment: KtCodeFragment): CodeFragmentParameterInfo {
        checkUsedOnce()

        codeFragment.accept(object : KtTreeVisitor<Unit>() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, data: Unit?): Void? {
                if (runReadAction { expression.isDotReceiver() }) {
                    return null
                }

                val target = bindingContext[BindingContext.REFERENCE_TARGET, expression]
                if (target is DeclarationDescriptorWithVisibility && target.visibility == Visibilities.LOCAL) {
                    val declarationPsiElement = target.toSourceElement.getPsi()
                    if (declarationPsiElement != null) {
                        runReadAction {
                            if (!codeFragment.isAncestor(declarationPsiElement, true)) {
                                analyzeSimpleNameExpression(expression, target, declarationPsiElement)
                            }
                        }
                    }
                }

                return null
            }
        }, Unit)

        return CodeFragmentParameterInfo(parameters.values.toList(), mappings, crossingBounds)
    }

    private fun analyzeSimpleNameExpression(expression: KtSimpleNameExpression, target: DeclarationDescriptor, targetPsi: PsiElement) {
        val type = when (target) {
            is ValueDescriptor -> target.type
            else -> return
        }

        val parameter = parameters.getOrPut(target) { Parameter(parameters.size, target.name, type) }
        mappings[expression] = parameter

        if (doesCrossInlineBounds(expression, targetPsi)) {
            crossingBounds += parameter
        }
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