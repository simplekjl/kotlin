/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.expressions.impl

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirAbstractElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirCatch
import org.jetbrains.kotlin.fir.expressions.FirTryExpression

class FirTryExpressionImpl(
    session: FirSession,
    psi: PsiElement?,
    override val tryBlock: FirBlock,
    override val finallyBlock: FirBlock?
) : FirAbstractElement(session, psi), FirTryExpression {
    override val catches = mutableListOf<FirCatch>()
}