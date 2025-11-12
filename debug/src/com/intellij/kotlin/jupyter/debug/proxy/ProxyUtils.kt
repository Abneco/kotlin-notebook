// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.kotlin.jupyter.debug.util.findFieldByName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiExpression
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.kotlin.idea.debugger.core.invokeInManagerThread


/**
 * Updates the descriptor for a proxy value if it implements [JdiDescriptorAwareProxy].
 *
 * Value origin is taken into account:
 * - [JdiDescriptorValueOrigin.MethodEvaluation] — for the evaluated result value.
 * - [JdiDescriptorValueOrigin.FieldValue] — for the container (owner) of the field named [name].
 */
internal fun Any.updateDescriptorWithArbitraryValueIfPossible(
    objectReference: ObjectReference,
    debugProcess: DebugProcessImpl,
    name: String,
    evaluationContext: EvaluationContextImpl?,
    valueOrigin: JdiDescriptorValueOrigin,
): Any {
    if (this !is JdiDescriptorAwareProxy) return this

    debugProcess.invokeInManagerThread {
         val descriptor = when (valueOrigin) {
            JdiDescriptorValueOrigin.MethodEvaluation -> {
                MethodResultValueDescriptor(
                    debugProcess.project,
                    name,
                    objectReference
                )
            }

            JdiDescriptorValueOrigin.FieldValue -> {
                val field = objectReference.findFieldByName(name) ?: return@invokeInManagerThread
                FieldDescriptorImpl(
                    debugProcess.project,
                    objectReference,
                    field,
                )
            }
        }

        if (evaluationContext != null) {
            descriptor.setContext(evaluationContext)
        }
        bindDescriptor(descriptor)
    }

    return this
}

/**
 * Simple ValueDescriptor implementation for method results.
 */
private class MethodResultValueDescriptor(
    project: Project,
    private val name: String,
    private val value: Value
) : ValueDescriptorImpl(project) {
    override fun calcValueName(): String? {
        return name
    }

    override fun calcValue(evaluationContext: EvaluationContextImpl?): Value {
        return value
    }

    override fun getDescriptorEvaluation(context: DebuggerContext?): PsiExpression? {
        throw UnsupportedOperationException("This method should not be called")
    }
}