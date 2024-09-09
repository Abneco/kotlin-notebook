// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.openapi.util.Disposer
import com.intellij.jupyter.core.jupyter.connections.JupyterConnectionParameters
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterServer
import com.intellij.jupyter.core.jupyter.nbformat.JupyterKernelSpec
import java.io.File

class KotlinJupyterServer(
    override val connectionParameters: JupyterConnectionParameters
): JupyterServer {
    override val client: JupyterClient by lazy {
        KotlinInProcessJupyterClient(File("")).also {
            Disposer.register(this, it)
        }
    }

    override val kernelSpecs: List<JupyterKernelSpec>
        get() = client.getKernelSpecs()

    override val defaultKernelSpec: JupyterKernelSpec?
        get() = kernelSpecs.find { kernelSpec -> kernelSpec.name == client.getDefaultKernelSpec() }

    override fun updateKernelSpecs() {
    }

    override fun dispose() {
    }
}
