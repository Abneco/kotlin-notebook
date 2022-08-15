// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.session

import org.jetbrains.plugins.notebooks.jupyter.connections.JupyterConnectionParameters
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterServer
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterServersFactory

class KotlinServersFactory: JupyterServersFactory {
    override fun create(connectionParameters: JupyterConnectionParameters): JupyterServer? {
        return if (connectionParameters.kernelName == "kotlin") KotlinJupyterServer(connectionParameters) else null
    }
}
