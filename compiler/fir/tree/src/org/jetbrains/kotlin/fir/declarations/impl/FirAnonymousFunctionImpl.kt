/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations.impl

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.types.FirType

class FirAnonymousFunctionImpl(
    session: FirSession,
    psi: PsiElement?,
    override val returnType: FirType,
    override val receiverType: FirType?
) : FirAbstractFunction(session, psi), FirAnonymousFunction, FirModifiableFunction