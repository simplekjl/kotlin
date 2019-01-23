/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.expressions.impl

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirMemberReference
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.transformSingle
import org.jetbrains.kotlin.fir.visitors.FirTransformer

class FirFunctionCallImpl(
    session: FirSession,
    psi: PsiElement?,
    override var safe: Boolean = false
) : FirAbstractCall(session, psi), FirFunctionCall, FirModifiableMemberAccess {
    override lateinit var calleeReference: FirMemberReference

    override var explicitReceiver: FirExpression? = null

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        calleeReference = calleeReference.transformSingle(transformer, data)
        explicitReceiver = explicitReceiver?.transformSingle(transformer, data)
        return super<FirAbstractCall>.transformChildren(transformer, data)
    }
}