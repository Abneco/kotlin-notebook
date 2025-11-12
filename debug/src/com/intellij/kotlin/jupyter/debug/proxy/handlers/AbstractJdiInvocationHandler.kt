// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.handlers

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.kotlin.jupyter.debug.proxy.JdiDescriptorValueOrigin
import com.intellij.kotlin.jupyter.debug.proxy.context.DebugValueContext
import com.intellij.kotlin.jupyter.debug.proxy.conversion.convertFromJdiValue
import com.intellij.kotlin.jupyter.debug.proxy.updateDescriptorWithArbitraryValueIfPossible
import com.intellij.kotlin.jupyter.debug.util.resolveReturnType
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import java.lang.reflect.Method

/**
 * Shared base for JDI invocation handlers that provides:
 * - unified access to [DebugValueContext]
 * - common conversion JDI -> host with return type resolution
 * - optional descriptor update
 *
 * This keeps concrete handlers focused on how to obtain the raw [Value].
 */
abstract class AbstractJdiInvocationHandler(
    protected val ctx: DebugValueContext,
) : JdiProxyInvocationHandler {
    final override val debugProcess: DebugProcessImpl = ctx.debugProcess
    final override val objectReference: ObjectReference = ctx.objectReference

    protected open val evaluationContext: EvaluationContextImpl? get() = ctx.evaluationContext

    /**
     * Converts [Value] to local value using method info and, if possible,
     * updates the descriptor.
     *
     * [origin] is used to determine a type of descriptor update.
     * [ownerReference] is used to bind descriptor:
     * - For FieldValue origin: container (owner) of the field
     * - For MethodEvaluation origin: the evaluated result itself
     */
    protected fun Value.convertValueAndBindToDescriptor(
        method: Method,
        name: String?,
        ownerReference: Value?,
        origin: JdiDescriptorValueOrigin,
    ): Any? {
        val typeInfo = method.resolveReturnType(ctx.genericType)
        val convertedValue = this.convertFromJdiValue(
            debugProcess,
            typeInfo,
            evaluationContext,
        ) ?: return null

        if (name != null && ownerReference as? ObjectReference != null) {
            convertedValue.updateDescriptorWithArbitraryValueIfPossible(
                ownerReference,
                debugProcess,
                name,
                evaluationContext,
                valueOrigin = origin,
            )
        }

        return convertedValue
    }
}
