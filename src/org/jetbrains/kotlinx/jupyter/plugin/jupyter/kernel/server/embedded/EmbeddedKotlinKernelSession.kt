// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.libraries.createLibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.DefaultKotlinKernelConfigFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelSession
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.asRawMessage
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.chooseJvmTargetForSnippets
import org.jetbrains.kotlinx.jupyter.plugin.settings.selectedKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.settings.toCanonicalString
import org.jetbrains.kotlinx.jupyter.repl.ReplConfig
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.createKernelPorts
import com.intellij.jupyter.core.jupyter.connections.execution.JupyterKernelCommunicationClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.kotlinx.jupyter.libraries.DefaultResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.messaging.MessageHandler
import java.nio.file.Path

class EmbeddedKotlinKernelSession(
    private val project: Project,
    override val sessionId: JupyterNotebookSessionId,
    private val notebookPath: Path,
    private val loggerFactory: EmbeddedKotlinKernelLoggerFactory,
    private val onMessage: (JupyterMessage) -> Unit
) : KotlinKernelSession, JupyterKernelCommunicationClient {

    private val messageHandler = createMessageHandler()

    override fun send(content: JupyterMessage) {
        content.asRawMessage { rawMessage, socketType ->
            messageHandler.handleMessage(socketType, rawMessage)
        }
    }

    override fun dispose() {
        close()
    }

    override fun close() {
        inMemoryHolderService.removeHolder(sessionId)
    }

    private val inMemoryHolderService get() = InMemoryReplResultsHolderService.getInstance(project)

    private fun createMessageHandler(): MessageHandler {
        val kernelConfig: KernelConfig = DefaultKotlinKernelConfigFactory(
            project,
            createKernelPorts { 0 },
            notebookPath
        ).create()

        val replConfig: ReplConfig = ReplConfig.create(
            DefaultResolutionInfoProviderFactory,
            loggerFactory,
            createLibraryHttpUtil(loggerFactory, IdeaHttpClient),
            kernelConfig.homeDir,
            kernelRunMode = IntellijProcessKernelRunMode,
        )

        val socketsManager = EmbeddedJupyterSockets(onMessage)

        val kernelVersion = project.selectedKernelVersion!!

        val jvmTargetForSnippets =
            chooseJvmTargetForSnippets(project)?.toCanonicalString() ?: defaultRuntimeProperties.jvmTargetForSnippets
        val runtimeProperties = IdeReplRuntimeProperties(
            kernelVersion,
            jvmTargetForSnippets
        )
        val replSettings = DefaultReplSettings(
            kernelConfig,
            replConfig,
            loggerFactory,
            runtimeProperties,
        )

        val inMemoryResultHolder = inMemoryHolderService.getOrCreateHolder(sessionId)
        return createEmbeddedMessageHandler(
            project,
            replSettings,
            loggerFactory,
            socketsManager,
            inMemoryResultHolder,
            kernelVersion.toMavenVersion(),
        )
    }
}
