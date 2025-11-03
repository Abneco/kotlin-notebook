// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.notebook

import com.intellij.kotlin.jupyter.core.debug.proxy.JdiProxyApiDelegate
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.state.VariableStateJdiProxy

interface JdiNotebookExtension : JdiProxyApiDelegate {
    val variablesHolderProxy: Map<String, VariableStateJdiProxy>
}