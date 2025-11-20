// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.notebook

import com.intellij.kotlin.jupyter.debug.proxy.JdiProxyApiDelegate
import com.intellij.kotlin.jupyter.debug.proxy.notebook.state.VariableStateJdiProxy

/**
 * API extension which adds new field for simplified access on a [JdiProxyApiDelegate]
 * for Variables States with a field access policy.
 *
 * @see NotebookJdiProxy
 */
interface JdiNotebookExtension : JdiProxyApiDelegate {
    val variablesHolderProxy: Map<String, VariableStateJdiProxy>
}