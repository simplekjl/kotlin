/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.expressions.impl

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirAbstractElement
import org.jetbrains.kotlin.fir.FirMemberReference
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirExpression

abstract class FirAbstractMemberAccess(
    session: FirSession,
    psi: PsiElement?,
    final override var safe: Boolean = false
) : FirAbstractElement(session, psi), FirModifiableMemberAccess {
    final override lateinit var calleeReference: FirMemberReference

    final override var explicitReceiver: FirExpression? = null
}