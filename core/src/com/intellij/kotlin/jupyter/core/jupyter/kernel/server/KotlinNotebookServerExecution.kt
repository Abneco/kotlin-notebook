// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.JupyterConnectionParameters
import com.intellij.jupyter.core.jupyter.connections.auth.token.JupyterTokenAuthParams
import com.intellij.jupyter.core.jupyter.connections.managed.state.JupyterServerStarted
import com.intellij.jupyter.core.jupyter.connections.managed.state.JupyterServerState
import com.intellij.jupyter.core.jupyter.connections.runtime.JupyterHttpParams
import com.intellij.jupyter.core.jupyter.server.common.JupyterServerExecution
import com.intellij.jupyter.core.jupyter.server.common.JupyterServerStateListener
import com.intellij.kotlin.jupyter.core.util.DEFAULT_KOTLIN_KERNEL_NAME
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import java.net.URI

private val emptyKotlinConnectionParameters = JupyterConnectionParameters(
    httpParams = JupyterHttpParams(URI.create(""), JupyterTokenAuthParams("")),
    serverPath = null,
    kernelName = DEFAULT_KOTLIN_KERNEL_NAME,
    configId = ""
)

class KotlinNotebookServerExecution : JupyterServerExecution {
    override val connectionDeferred: Deferred<JupyterConnectionParameters>
        get() = CompletableDeferred(emptyKotlinConnectionParameters)
    override val state: JupyterServerState
        get() = JupyterServerStarted(emptyKotlinConnectionParameters)

    override fun stopServer(): Job {
        return CompletableDeferred(Unit)
    }

    override fun addStateListener(listener: JupyterServerStateListener) {

    }
}
