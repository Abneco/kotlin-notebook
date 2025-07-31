// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.attached

import com.intellij.jupyter.core.jupyter.connections.session.KernelStartupOptions
import com.intellij.jupyter.execution.listeners.KernelInfoReplyReceivedEvent
import com.intellij.jupyter.execution.listeners.KernelListener
import com.intellij.jupyter.execution.kernel.KernelRunnableHandler
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.ModeAwareKernelRunnableFactory
import com.intellij.jupyter.execution.kernel.asRawMessage
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookAttachedModeOptions
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.jupyter.execution.util.jsonConfig
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.kotlinx.jupyter.messaging.KernelInfoReplyMetadata
import org.jetbrains.kotlinx.jupyter.startup.DEFAULT_SPRING_SIGNATURE_KEY

class AttachedKernelProcessFactory : ModeAwareKernelRunnableFactory(
    KotlinNotebookSessionRunMode.ATTACHED_PROCESS
) {
    override fun createSpecificKernelRunnableHandler(
        startupOptions: KernelStartupOptions,
    ): KernelRunnableHandler {
        val options = KotlinNotebookAttachedModeOptions.getInstance(startupOptions.project)
        val host = options.host
        val ports = options.getKernelPorts()

        val kernelConfig = AttachedKernelConfigFactory(
            startupOptions = startupOptions,
            host = host,
            ports = ports,
            signature = DEFAULT_SPRING_SIGNATURE_KEY
        ).create()

        return AttachedKernelProcessHandler(
            startupOptions, kernelConfig.jupyterParams
        ).apply {
            addBaseKernelListener(MyListener)
        }
    }

    private object MyListener : KernelListener {
        override fun kernelInfoReplyReceived(event: KernelInfoReplyReceivedEvent) {
            val kernel = event.eventSource
            val project = kernel.project
            val notebookFile = kernel.notebookVirtualFile ?: return
            val compilerService = JupyterCompilerService.getForFile(project, notebookFile)

            val message = event.message
            val replyMetadata = message.asRawMessage { rawMessage, _ ->
                val metadata = rawMessage.metadata ?: return@asRawMessage null
                jsonConfig.decodeFromJsonElement<KernelInfoReplyMetadata>(metadata)
            } ?: return

            val allSnippetsMetadata = replyMetadata.state
            compilerService.addCompiledSnippet(allSnippetsMetadata, null)
        }
    }
}