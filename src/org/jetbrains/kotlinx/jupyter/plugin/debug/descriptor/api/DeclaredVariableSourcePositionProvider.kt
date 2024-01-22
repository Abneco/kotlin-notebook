// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.api

import com.intellij.debugger.SourcePosition
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.NotebookVariableStateDescriptor

fun interface DeclaredVariableSourcePositionProvider {
    fun resolveTo(descriptor: NotebookVariableStateDescriptor): SourcePosition?
}