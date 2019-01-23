/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.expressions.impl

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.transformSingle
import org.jetbrains.kotlin.fir.visitors.FirTransformer

class FirSingleExpressionBlock(
    override val session: FirSession,
    private var expression: FirExpression
) : FirBlock {
    override val annotations: List<FirAnnotationCall> = listOf()

    override val statements
        get() = listOf(expression)

    override val psi: PsiElement?
        get() = expression.psi

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        expression = expression.transformSingle(transformer, data)
        return this
    }
}