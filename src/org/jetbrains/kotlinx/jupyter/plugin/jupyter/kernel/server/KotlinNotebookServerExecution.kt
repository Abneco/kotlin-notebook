// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import org.jetbrains.kotlinx.jupyter.plugin.util.DEFAULT_KOTLIN_KERNEL_NAME
import com.intellij.jupyter.core.jupyter.connections.JupyterConnectionParameters
import com.intellij.jupyter.core.jupyter.connections.JupyterConnectionParameters.Location.Direct
import com.intellij.jupyter.core.jupyter.server.common.JupyterServerExecution
import com.intellij.jupyter.core.jupyter.server.common.JupyterServerStarted
import com.intellij.jupyter.core.jupyter.server.common.JupyterServerState
import com.intellij.jupyter.core.jupyter.server.common.JupyterServerStateListener
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private val emptyKotlinConnectionParameters = JupyterConnectionParameters(
    location = Direct(URI.create("")),
    kernelName = DEFAULT_KOTLIN_KERNEL_NAME,
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
