// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.JupyterConnectionParameters
import com.intellij.jupyter.core.jupyter.connections.JupyterConnectionParameters.Location.Direct
import com.intellij.jupyter.core.jupyter.server.common.JupyterServerExecution
import com.intellij.jupyter.core.jupyter.server.common.JupyterServerStarted
import com.intellij.jupyter.core.jupyter.server.common.JupyterServerState
import com.intellij.jupyter.core.jupyter.server.common.JupyterServerStateListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import org.jetbrains.kotlinx.jupyter.plugin.util.DEFAULT_KOTLIN_KERNEL_NAME
import java.net.URI

private val emptyKotlinConnectionParameters = JupyterConnectionParameters(
    location = Direct(URI.create("")),
    kernelName = DEFAULT_KOTLIN_KERNEL_NAME,
    serverPath = null,
)

class KotlinNotebookServerExecution: JupyterServerExecution {
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
