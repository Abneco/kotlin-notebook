// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinKernelName
import com.intellij.jupyter.core.jupyter.connections.JupyterConnectionParameters
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterServer
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterServersFactory

class KotlinServersFactory: JupyterServersFactory {
    override fun create(connectionParameters: JupyterConnectionParameters): JupyterServer? {
        return if (isKotlinKernelName(connectionParameters.kernelName)) KotlinJupyterServer(connectionParameters) else null
    }
}
