// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.api

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.ui.tree.FieldDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

fun interface DeclaredVariableSourcePositionProvider {
    fun resolveTo(project: Project, virtualFile: VirtualFile?, descriptor: FieldDescriptor): SourcePosition?
}