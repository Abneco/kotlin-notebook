// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.JupyterConnectionParameters
import com.intellij.jupyter.core.jupyter.connections.client.JupyterClient
import com.intellij.jupyter.core.jupyter.connections.server.JupyterServer
import com.intellij.openapi.util.Disposer

class KotlinJupyterServer(
    override val connectionParameters: JupyterConnectionParameters
): JupyterServer {
    override val client: JupyterClient by lazy {
        KotlinInProcessJupyterClient().also {
            Disposer.register(this, it)
        }
    }
}
