// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.session

import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.notebooks.jupyter.connections.JupyterConnectionParameters
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterServer
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterKernelSpec
import java.io.File

class KotlinJupyterServer(
    override val connectionParameters: JupyterConnectionParameters
): JupyterServer {
    private val lock: Any = Object()

    /**
     * Guarded by [lock].
     */
    private var _client: JupyterClient? = null

    override val client: JupyterClient
        get() = synchronized(lock) {
            _client ?: KotlinInProcessJupyterClient(File("")).also {
                _client = it
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