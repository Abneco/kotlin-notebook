// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.JupyterConnectionParameters
import com.intellij.jupyter.core.jupyter.connections.server.JupyterServer
import com.intellij.jupyter.core.jupyter.connections.server.JupyterServersFactory
import com.intellij.kotlin.jupyter.core.util.isKotlinKernelName

class KotlinServersFactory: JupyterServersFactory {
    override fun create(connectionParameters: JupyterConnectionParameters): JupyterServer? {
        return if (isKotlinKernelName(connectionParameters.serverType)) KotlinJupyterServer(connectionParameters) else null
    }
}
