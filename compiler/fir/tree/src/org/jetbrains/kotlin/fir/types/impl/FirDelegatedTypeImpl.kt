/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types.impl

import org.jetbrains.kotlin.fir.FirAbstractElement
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.types.FirDelegatedType
import org.jetbrains.kotlin.fir.types.FirType

class FirDelegatedTypeImpl(
    override val type: FirType,
    override val delegate: FirExpression?
) : FirAbstractElement(type.session, type.psi), FirDelegatedType {
    override val annotations: List<FirAnnotationCall>
        get() = type.annotations
}