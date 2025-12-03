// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.JupyterConnectionParameters
import com.intellij.jupyter.core.jupyter.connections.auth.token.JupyterTokenAuthParams
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.runtime.JupyterHttpParams
import com.intellij.jupyter.execution.kernel.JupyterNotebookKernelSessionFactory
import com.intellij.jupyter.execution.process.SeparateJupyterKernelClient
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.messages.updateNotebookMetadata
import com.intellij.kotlin.jupyter.core.util.DEFAULT_KOTLIN_KERNEL_NAME
import com.intellij.kotlin.jupyter.core.util.isKotlinKernelName
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.net.URI

class KotlinKernelSessionFactory : JupyterNotebookKernelSessionFactory() {

    override suspend fun afterSessionCreation(session: JupyterNotebookSession) {
        if (!session.updateNotebookMetadata()) {
            thisLogger().warn("Failed to update Kotlin Notebook with IntelliJ metadata")
        }
    }

    override suspend fun checkIsSupported(
        file: BackedNotebookVirtualFile,
        project: Project
    ): Boolean {
        val kernelName = file.notebook.kernelSpec?.name
        return isKotlinKernelName(kernelName) || file.isKotlinNotebook
    }

    override fun createKernelClient(): SeparateJupyterKernelClient {
        return KotlinInProcessJupyterClient()
    }

    override fun createConnectionParameters(): JupyterConnectionParameters = JupyterConnectionParameters(
        httpParams = JupyterHttpParams(URI.create(""), JupyterTokenAuthParams(""), authority = ""),
        serverType = DEFAULT_KOTLIN_KERNEL_NAME,
        configId = ""
    )
}