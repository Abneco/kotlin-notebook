// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.libraries.createLibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.libraries.getDefaultClasspathResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.DefaultKotlinKernelConfigFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelSession
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.asRawMessage
import org.jetbrains.kotlinx.jupyter.repl.ReplConfig
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.createKernelPorts
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterKernelCommunicationClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import java.nio.file.Path

class EmbeddedKotlinKernelSession(
    private val project: Project,
    override val sessionId: JupyterNotebookSessionId,
    private val notebookPath: Path,
    private val loggerFactory: KernelLoggerFactory,
    private val onMessage: (JupyterMessage) -> Unit
) : KotlinKernelSession, JupyterKernelCommunicationClient {

    private val messageHandler = run {
        val kernelConfig: KernelConfig = DefaultKotlinKernelConfigFactory(
            project,
            createKernelPorts { 0 },
            notebookPath
        ).create()

        val replConfig: ReplConfig = ReplConfig.create(
            ::getDefaultClasspathResolutionInfoProvider,
            loggerFactory,
            createLibraryHttpUtil(loggerFactory, IdeaHttpClient),
            kernelConfig.homeDir
        )

        val socketsManager = EmbeddedJupyterSockets(onMessage)

        val replSettings = DefaultReplSettings(
            kernelConfig,
            replConfig
        )
        createEmbeddedMessageHandler(project, replSettings, loggerFactory, socketsManager)
    }


    override fun send(content: JupyterMessage) {
        content.asRawMessage { rawMessage, socketType ->
            messageHandler.handleMessage(socketType, rawMessage)
        }
    }

    override fun dispose() {
    }

    override fun close() {
    }
}
