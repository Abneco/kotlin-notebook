// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.notebook

import com.intellij.kotlin.jupyter.debug.proxy.JdiFieldAccessPath
import com.intellij.kotlin.jupyter.debug.proxy.repl.context.SharedReplContextJdiProxy
import com.sun.jdi.ObjectReference
import org.jetbrains.kotlinx.jupyter.api.Notebook

/**
 * This class mimics the [Notebook] API, with special getters which are suitable for JDI.
 *
 * @see [com.intellij.kotlin.jupyter.debug.proxy.handlers.delegates.notebook.JdiNotebookDelegateHandler]
 */
interface NotebookJdiProxy : Notebook, JdiNotebookExtension {

    @get:JdiFieldAccessPath("sharedReplContext.evaluator.variablesHolder")
    val variablesHolderReference: ObjectReference

    @get:JdiFieldAccessPath("sharedReplContext")
    val sharedReplContext: SharedReplContextJdiProxy?
}
