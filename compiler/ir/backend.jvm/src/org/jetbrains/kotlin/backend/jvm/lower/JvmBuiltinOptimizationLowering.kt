/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.codegen.intrinsics.Not
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.descriptors.IrTemporaryVariableDescriptor
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class JvmBuiltinOptimizationLowering(val context: JvmBackendContext) : FileLoweringPass {

    companion object {
        fun isNegation(expression: IrExpression, context: JvmBackendContext): Boolean {
            // TODO: there should be only one representation of the 'not' operator.
            return expression is IrCall &&
                    (expression.symbol == context.irBuiltIns.booleanNotSymbol ||
                            context.state.intrinsics.getIntrinsic(expression.symbol.descriptor) is Not)
        }

        fun negationArgument(call: IrCall): IrExpression {
            // TODO: there should be only one representation of the 'not' operator.
            // Once there is only the IR definition the negation argument will
            // always be a value argument and this method can be removed.
            return call.dispatchReceiver ?: call.getValueArgument(0)!!
        }
    }

    private fun hasNoSideEffectsForNullCompare(expression: IrExpression): Boolean {
        return expression.type.isPrimitiveType() && (expression is IrConst<*> || expression is IrGetValue)
    }

    private fun isNullCheckOfPrimitiveTypeValue(call: IrCall, context: JvmBackendContext): Boolean {
        if (call.symbol == context.irBuiltIns.eqeqSymbol) {
            val left = call.getValueArgument(0)!!
            val right = call.getValueArgument(1)!!
            // When used for null checks, it is safe to eliminate constants and local variable loads.
            // Even if a local variable of simple type is updated via the debugger it still cannot
            // be null.
            return (right.isNullConst() && left.type.isPrimitiveType())
                    || (left.isNullConst() && right.type.isPrimitiveType())
        }
        return false
    }

    private fun isNullCheckOfNullConstant(call: IrCall, context: JvmBackendContext): Boolean {
        if (call.symbol == context.irBuiltIns.eqeqSymbol) {
            val left = call.getValueArgument(0)!!
            val right = call.getValueArgument(1)!!
            return right.isNullConst() && left.isNullConst()
        }
        return false
    }

    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                expression.transformChildrenVoid(this)
                return if (isNegation(expression, context) && isNegation(negationArgument(expression), context)) {
                    // TODO: This lowering is currently JvmBackend specific because there are multiple
                    // definitions of the boolean 'not' operator. Once there is only the irBuiltins
                    // definition this lowering could be shared with other backends.
                    negationArgument(negationArgument(expression) as IrCall)
                } else if (isNullCheckOfPrimitiveTypeValue(expression, context)) {
                    val left = expression.getValueArgument(0)!!
                    val nonNullArgument = if (left.isNullConst()) expression.getValueArgument(1)!! else left
                    val constFalse = IrConstImpl.constFalse(expression.startOffset, expression.endOffset, context.irBuiltIns.booleanType)
                    if (hasNoSideEffectsForNullCompare(nonNullArgument)) {
                        constFalse
                    } else {
                        IrBlockImpl(expression.startOffset, expression.endOffset, expression.type, expression.origin).apply {
                            statements.add(nonNullArgument.coerceToUnitIfNeeded(nonNullArgument.type.toKotlinType(), context.irBuiltIns))
                            statements.add(constFalse)
                        }
                    }
                } else if (isNullCheckOfNullConstant(expression, context)) {
                    IrConstImpl.constTrue(expression.startOffset, expression.endOffset, context.irBuiltIns.booleanType)
                } else {
                    expression
                }
            }

            override fun visitWhen(expression: IrWhen): IrExpression {
                expression.transformChildrenVoid(this)
                // Remove all branches with constant false condition.
                expression.branches.removeIf() {
                    it.condition.isFalseConst()
                }
                // If the only condition that is left has a constant true condition remove the
                // when in favor of the result. If there are no conditions left, remove the when
                // entirely and replace it with an empty block.
                if (expression.branches.size == 1) {
                    val branch = expression.branches.first()
                    if (branch.condition.isTrueConst()) return branch.result
                }
                return if (expression.branches.size == 0)
                    IrBlockImpl(expression.startOffset, expression.endOffset, context.irBuiltIns.unitType)
                else
                    expression
            }

            private fun isImmutableTemporaryVariableWithConstantValue(statement: IrStatement): Boolean {
                return statement is IrVariable &&
                        statement.descriptor is IrTemporaryVariableDescriptor &&
                        !statement.isVar &&
                        statement.initializer is IrConst<*>
            }

            override fun visitBlock(expression: IrBlock): IrExpression {
                expression.transformChildrenVoid(this)
                // Remove declarations of immutable temporary variables with constant values.
                // IrGetValue operations for such temporary variables are replaced
                // by the initializer IrConst.
                expression.statements.removeIf {
                    isImmutableTemporaryVariableWithConstantValue(it)
                }
                // Remove a block that contains only two statements: the declaration of a temporary
                // variable and a load of the value of that temporary variable with just the initializer
                // for the temporary variable. We only perform this transformation for compiler generated
                // temporary variables. Local variables can be changed at runtime and therefore eliminating
                // an actual local variable changes debugging behavior.
                if (expression.statements.size == 2) {
                    val first = expression.statements[0]
                    val second = expression.statements[1]
                    if (first is IrVariable
                        && first.descriptor is IrTemporaryVariableDescriptor
                        && second is IrGetValue
                        && first.symbol == second.symbol) {
                        expression.statements.clear()
                        first.initializer?.let { expression.statements.add(it) }
                    }
                }
                return expression
            }

            override fun visitGetValue(expression: IrGetValue): IrExpression {
                // Replace IrGetValue of an immutable temporary variable with a constant
                // initializer with the constant initializer.
                val variable = expression.symbol.owner
                return if (isImmutableTemporaryVariableWithConstantValue(variable))
                    (variable as IrVariable).initializer!!
                else
                    expression
            }
        })
    }
}