// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.session

import org.jetbrains.plugins.notebooks.jupyter.connections.JupyterConnectionParameters
import org.jetbrains.plugins.notebooks.jupyter.connections.JupyterConnectionParameters.Location.Direct
import org.jetbrains.plugins.notebooks.jupyter.server.JupyterServerExecution
import org.jetbrains.plugins.notebooks.jupyter.server.JupyterServerStarted
import org.jetbrains.plugins.notebooks.jupyter.server.JupyterServerState
import org.jetbrains.plugins.notebooks.jupyter.server.JupyterServerStateListener
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val emptyKotlinConnectionParameters = JupyterConnectionParameters(
    location = Direct(URI.create("")),
    kernelName = "kotlin",
    serverPath = null,
)

class KotlinNotebookServerExecution: JupyterServerExecution {
    override val connectionFuture: Future<JupyterConnectionParameters>
        get() = CompletableFuture.completedFuture(emptyKotlinConnectionParameters)
    override val state: JupyterServerState
        get() = JupyterServerStarted(emptyKotlinConnectionParameters)

    override fun stopServer(): Future<*> {
        return CompletableFuture.completedFuture(null)
    }

    override fun addStateListener(listener: JupyterServerStateListener) {

    }
}
