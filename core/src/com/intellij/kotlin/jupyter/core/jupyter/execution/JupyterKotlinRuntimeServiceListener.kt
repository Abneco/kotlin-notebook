// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.execution

import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterRuntimeListener
import com.intellij.kotlin.jupyter.core.projectModel.JupyterKotlinProjectArtifactsService
import com.intellij.kotlin.jupyter.core.settings.SessionOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.generateSnippet
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookCodegen
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebookSession
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger

class JupyterKotlinRuntimeServiceListener : JupyterRuntimeListener {
    override fun sessionCreated(session: JupyterNotebookSession) {
        if (!session.isKotlinNotebookSession()) return

        JupyterKotlinProjectArtifactsService.getInstance(session.project).registerSession(session)

        val initCode = """
            ${service<SessionOptionsProvider>().generateSnippet()}
            ${KotlinNotebookCodegen.generateColorSchemeChangeCode()}
        """.trimIndent()

        val callbacks = session.virtualFile?.let { virtualFile ->
            val project = session.project
            listOf(
              KotlinNotebookCellExecutionCallbackFactory.getInstance().createUnboundCallback(project, virtualFile),
              object : JupyterExecutionCallbackAdapter() {
                    override fun onExecuteReply(message: JupyterMessage) {
                        logger<JupyterKotlinRuntimeServiceListener>().debug("Kotlin session has been initialized with response: ${message.json}")
                    }
                })
        } ?: emptyList()

        session.execute(initCode, onMessageCreated = {}, callbacks = callbacks, silent = true)
    }
}