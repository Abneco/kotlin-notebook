// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl
import com.sun.jdi.ArrayReference
import com.sun.jdi.Value
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.api.KotlinNotebookValueDescriptor

class NotebookArrayElementDescriptor(
    private val debugProcessImpl: DebugProcessImpl,
    private val value: Value,
    arrayReference: ArrayReference,
    index: Int,
) : ArrayElementDescriptorImpl(debugProcessImpl.project, arrayReference, index), KotlinNotebookValueDescriptor {
    override fun calcValue(evaluationContext: EvaluationContextImpl?): Value? {
        return value
    }

    override fun isValueReady(): Boolean {
        return true
    }

    override fun getValue(): Value {
        return value
    }

    override val debuggerSession: DebuggerSession
        get() = debugProcessImpl.session
}