// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.kernel.session.JupyterNotebookSessionFactory
import com.intellij.jupyter.core.jupyter.connections.JupyterConnectionParameters
import com.intellij.jupyter.core.jupyter.connections.auth.token.JupyterTokenAuthParams
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.runtime.JupyterHttpParams
import com.intellij.jupyter.core.jupyter.connections.session.JupyterSessionData
import com.intellij.jupyter.core.jupyter.connections.session.SessionStartupOptionsImpl
import com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.JupyterWebOutputApi
import com.intellij.jupyter.core.jupyter.helper.JupyterHelper
import com.intellij.jupyter.core.jupyter.nbformat.JupyterKernelSpec
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process.KotlinNotebookSessionLaunchStrategy
import com.intellij.kotlin.jupyter.core.util.DEFAULT_KOTLIN_KERNEL_NAME
import com.intellij.kotlin.jupyter.core.util.isKotlinKernelName
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.project.Project
import java.net.URI
import java.nio.file.Paths

class KotlinKernelSessionFactory : JupyterNotebookSessionFactory {
    private val kotlinConnectionParameters = JupyterConnectionParameters(
        httpParams = JupyterHttpParams(URI.create(""), JupyterTokenAuthParams(""), authority = ""),
        serverType = DEFAULT_KOTLIN_KERNEL_NAME,
        configId = ""
    )


    override suspend fun buildSession(project: Project, file: BackedNotebookVirtualFile): JupyterNotebookSession? {
        val kernelName = file.notebook.kernelSpec?.name
        if (!isKotlinKernelName(kernelName) && !file.isKotlinNotebook)
            return null


        val connectionParameters = kotlinConnectionParameters
        val jupyterClientManager = KotlinInProcessJupyterClientManager.getInstance(project)
        val pythonKernel = jupyterClientManager.kernels.first()
        val notebookPath = Paths.get(file.file.path)
        val startupOptions = SessionStartupOptionsImpl(
            project = project,
            notebookPath = notebookPath,
            notebookVirtualFile = file
        )

        val notebookSession = KotlinNotebookSessionLaunchStrategy().createAndVerifySession(
            jupyterClientManager,
            {
                createSessionData(pythonKernel.name, startupOptions)
            },
            { data -> createSessionWithData(project, data, file, connectionParameters, pythonKernel) }
        )
        return notebookSession
    }

    private fun createSessionWithData(
        project: Project,
        data: JupyterSessionData,
        file: BackedNotebookVirtualFile,
        connectionParameters: JupyterConnectionParameters,
        kernelSpec: JupyterKernelSpec,
    ): JupyterNotebookSession {
        val notebookSession = JupyterNotebookSession(
            project = project,
            virtualFile = file,
            jupyterClientManager = KotlinInProcessJupyterClientManager.getInstance(project),
            kernelSpec = kernelSpec,
            kernelId = data.kernelId,
            sessionId = data.sessionId,
            pathFromRoot = data.path,
            connectionParameters = connectionParameters,
        ) {
            JupyterHelper
                .getJupyterEditorByOriginalVirtualFile(file.originFile)
                ?.let { JupyterWebOutputApi.get(it).frontEndApi }
        }
        return notebookSession
    }


}