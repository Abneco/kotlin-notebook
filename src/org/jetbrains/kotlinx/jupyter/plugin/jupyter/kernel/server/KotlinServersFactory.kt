// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinKernelName
import org.jetbrains.plugins.notebooks.jupyter.connections.JupyterConnectionParameters
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterServer
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterServersFactory

class KotlinServersFactory: JupyterServersFactory {
    override fun create(connectionParameters: JupyterConnectionParameters): JupyterServer? {
        return if (isKotlinKernelName(connectionParameters.kernelName)) KotlinJupyterServer(connectionParameters) else null
    }
}
