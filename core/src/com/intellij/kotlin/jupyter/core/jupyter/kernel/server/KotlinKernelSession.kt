// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.execution.JupyterKernelCommunicationClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.openapi.Disposable

interface KotlinKernelSession: JupyterKernelCommunicationClient, Disposable {
    val sessionId: JupyterNotebookSessionId
}
