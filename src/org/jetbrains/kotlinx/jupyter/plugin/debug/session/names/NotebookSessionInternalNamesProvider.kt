// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.session.names

/**
 * Used for determining [Location] for placing a breakpoint
 * inside the separate thread inside Kotlin Kernel.
 */
internal object KotlinNotebookSessionInternalNamesProvider  {
    const val notebookClassName: String
        = "org.jetbrains.kotlinx.jupyter.repl.notebook.impl.NotebookImpl"
    const val notebookDebugThreadName: String
        = "NotebookDebugThread"
    const val notebookDebugMethodName: String
        = "\$debugMethod"
    const val notebookDebugMethodBreakpointLineNumber: Int = 48
}