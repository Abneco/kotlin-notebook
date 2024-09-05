// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterKernelCommunicationClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId

interface KotlinKernelSession: JupyterKernelCommunicationClient, Disposable {
    val sessionId: JupyterNotebookSessionId
}
