// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.session.names

interface NotebookSessionInternalNamesProvider {
    val notebookClassName: String
    val notebookDebugThreadName: String
    val notebookDebugMethodName: String
    val sharedContextClassName: String
}

internal object KotlinNotebookSessionInternalNamesProvider : NotebookSessionInternalNamesProvider {
    override val notebookClassName: String
        = "org.jetbrains.kotlinx.jupyter.repl.notebook.impl.NotebookImpl"
    override val notebookDebugThreadName: String
        = "NotebookDebugThread"
    override val notebookDebugMethodName: String
        = "\$debugMethod"
    override val sharedContextClassName: String
        get() = ""
}