// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.variables

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.SourcePositionProvider
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.ui.tree.FieldDescriptor
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.NotebookVariableDescriptorPositionResolver
import org.jetbrains.kotlinx.jupyter.plugin.debug.session.KotlinNotebookDebugSessionManager
import org.jetbrains.kotlinx.jupyter.plugin.util.toBackedNotebookFile

class NotebookFieldSourcePositionProvider : SourcePositionProvider() {
    override fun computeSourcePosition(
        descriptor: NodeDescriptor,
        project: Project,
        context: DebuggerContextImpl,
        nearest: Boolean
    ): SourcePosition? {
        if (descriptor !is FieldDescriptor) return null
        val session = KotlinNotebookDebugSessionManager.getInstance(project).getByDebugProcessOrNull(context.debugProcess) ?: return null
        val notebookFile = session.virtualFile.file.toBackedNotebookFile() ?: return null

        return NotebookVariableDescriptorPositionResolver.resolveTo(project, notebookFile, descriptor)
    }
}