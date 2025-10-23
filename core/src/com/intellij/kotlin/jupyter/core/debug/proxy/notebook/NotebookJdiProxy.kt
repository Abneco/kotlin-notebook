// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.notebook

import com.intellij.kotlin.jupyter.core.debug.proxy.JdiObjectReferenceProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.state.VariableStateJdiProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.repl.context.SharedReplContextProvider
import com.sun.jdi.ObjectReference
import org.jetbrains.kotlinx.jupyter.api.Notebook

/**
 * This class mimics the [Notebook] API, with special getters which are suitable for JDI.
 *
 * @see [com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook.NotebookJdiProxyInvocationHandler]
 */
interface NotebookJdiProxy : SharedReplContextProvider, Notebook, JdiObjectReferenceProxy {
    val variablesHolderProxy: Map<String, VariableStateJdiProxy>
    val variablesHolderReference: ObjectReference
}
