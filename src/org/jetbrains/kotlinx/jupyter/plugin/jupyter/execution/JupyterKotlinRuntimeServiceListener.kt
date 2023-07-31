// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.execution

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.isKotlinNotebookSession
import org.jetbrains.kotlinx.jupyter.plugin.util.KotlinNotebookCodegen
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage

class JupyterKotlinRuntimeServiceListener : JupyterRuntimeService.Listener {
    override fun sessionCreated(session: JupyterNotebookSession) {
        if (!session.isKotlinNotebookSession()) return

        JupyterKotlinProjectArtifactsService.getInstance(session.project).registerSession(session)

        val initCode = """
                        ${KotlinNotebookCodegen.generateSessionOptions(resolveSources = true, serializeScriptData = true)}
                        ${KotlinNotebookCodegen.generateColorSchemeChangeCode()}
                    """.trimIndent()

        val callbacks = session.virtualFile?.let { virtualFile ->
            val project = session.project
            listOf(
              KotlinNotebookCellExecutionCallbackFactory.getInstance().createNotBoundCallback(project, virtualFile),
              object : JupyterExecutionCallbackAdapter() {
                    override fun onExecuteReply(message: JupyterMessage) {
                        logger<JupyterKotlinRuntimeServiceListener>().debug("Kotlin session has been initialized with response: ${message.json}")
                    }
                })
        } ?: emptyList()

        session.execute(initCode, onMessageCreated = {}, callbacks = callbacks, silent = true)
    }
}