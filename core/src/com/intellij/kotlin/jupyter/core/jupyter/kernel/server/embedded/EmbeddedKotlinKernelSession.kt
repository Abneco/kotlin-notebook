// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded

import com.intellij.jupyter.core.jupyter.connections.execution.JupyterKernelCommunicationClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelSession
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.asRawMessage
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.chooseJvmTargetForSnippets
import com.intellij.kotlin.jupyter.core.settings.selectedKernelVersion
import com.intellij.kotlin.jupyter.core.settings.toCanonicalString
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.EDT
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.libraries.DefaultResolutionInfoProviderFactory
import org.jetbrains.kotlinx.jupyter.libraries.createLibraryHttpUtil
import org.jetbrains.kotlinx.jupyter.messaging.MessageHandler
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelConfig
import org.jetbrains.kotlinx.jupyter.repl.ReplConfig
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParams

class EmbeddedKotlinKernelSession(
    private val project: Project,
    private val kernelConfig: KernelConfig<KotlinKernelOwnParams>,
    override val sessionId: JupyterNotebookSessionId,
    private val loggerFactory: EmbeddedKotlinKernelLoggerFactory,
    private val onMessage: (JupyterMessage) -> Unit
) : KotlinKernelSession, JupyterKernelCommunicationClient {

    private val messageHandler = createMessageHandler()

    override fun send(content: JupyterMessage) {
        runOnBackgroundThread {
            doSend(content)
        }
    }

    override fun dispose() {
    }

    override fun close() {
        Disposer.dispose(this)
    }

    private fun createMessageHandler(): MessageHandler {
        val intellijDataProvider = IntellijDataProvider(
            currentProject = project,
        )
        Disposer.register(this, intellijDataProvider)

        val replConfig: ReplConfig = ReplConfig.create(
            DefaultResolutionInfoProviderFactory,
            loggerFactory,
            createLibraryHttpUtil(loggerFactory, IdeaHttpClient),
            kernelRunMode = IntellijProcessKernelRunMode(intellijDataProvider),
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

        val inMemoryHolderService = InMemoryReplResultsHolderService.getInstance(project)
        val inMemoryResultHolder = inMemoryHolderService.getOrCreateHolder(sessionId, this)
        return createEmbeddedMessageHandler(
            project,
            replSettings,
            loggerFactory,
            socketsManager,
            inMemoryResultHolder,
            kernelVersion.toMavenVersion(),
        )
    }

    /**
     * "Sends" a message to the kernel.
     * In fact, in embedded mode the message is processed in the same thread synchronously.
     * That's why we should never do this on EDT: it would lead to UI freezes.
     */
    @RequiresBackgroundThread
    private fun doSend(content: JupyterMessage) {
        content.asRawMessage { rawMessage, socketType ->
            messageHandler.handleMessage(socketType, rawMessage)
        }
    }

    private fun runOnBackgroundThread(action: () -> Unit) {
        if (EDT.isCurrentThreadEdt()) {
            application.executeOnPooledThread(action)
        } else {
            action()
        }
    }
}
