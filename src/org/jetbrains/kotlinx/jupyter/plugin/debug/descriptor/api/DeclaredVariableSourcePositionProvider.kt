// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.api

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.ui.tree.FieldDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

/**
 * Used for resolve [SourcePosition] based on [FieldDescriptor].
 * Contract:
 *  [FieldDescriptor] is a descriptor of Kernel interpreter state.
 *  [BackedNotebookVirtualFile] is Kotlin Notebook
 *  [SourcePosition] is located inside [BackedNotebookVirtualFile], or null otherwise
 */
fun interface DeclaredVariableSourcePositionProvider {
    fun resolveTo(project: Project, virtualFile: BackedNotebookVirtualFile, descriptor: FieldDescriptor): SourcePosition?
}