// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.repl.context

/**
 * [org.jetbrains.kotlinx.jupyter.repl.SharedReplContext] is present only in [org.jetbrains.kotlinx.jupyter.repl.notebook.impl.NotebookImpl]
 */
interface SharedReplContextProvider {
    val sharedReplContext: SharedReplContextJdiProxy?
}