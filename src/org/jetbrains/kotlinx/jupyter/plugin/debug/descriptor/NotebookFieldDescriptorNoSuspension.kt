// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.openapi.project.Project
import com.sun.jdi.Field
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.api.KotlinNotebookValueDescriptor
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

class NotebookFieldDescriptorNoSuspension(
    private val session: DebuggerSession,
    val virtualFile: BackedNotebookVirtualFile?,
    project: Project,
    objectReference: ObjectReference,
    field: Field,
    private val value: Value?
) : FieldDescriptorImpl(project, objectReference, field), KotlinNotebookValueDescriptor {
    override fun calcValue(evaluationContext: EvaluationContextImpl?): Value? {
        return value
    }

    override fun getValue(): Value? = value

    override fun isValueReady(): Boolean = true

    val sourcePosition: SourcePosition? by lazy {
        NotebookVariableDescriptorPositionResolver.resolveTo(project, virtualFile?.file, this)
    }
    override val debuggerSession: DebuggerSession
        get() = session
}