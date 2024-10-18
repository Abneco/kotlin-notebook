// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.attached

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KernelInfoReplyReceivedEvent
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelListener
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelRunnableHandler
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.ModeAwareKernelRunnableFactory
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.asRawMessage
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookAttachedModeOptions
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.core.util.jsonConfig
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.kotlinx.jupyter.messaging.KernelInfoReplyMetadata
import org.jetbrains.kotlinx.jupyter.startup.DEFAULT_SPRING_SIGNATURE_KEY
import org.jetbrains.kotlinx.jupyter.startup.createClientKotlinKernelConfig
import java.nio.file.Path

class AttachedKernelProcessFactory : ModeAwareKernelRunnableFactory(
    KotlinNotebookSessionRunMode.ATTACHED_PROCESS
) {
    override fun createKernelRunnableHandler(
        project: Project,
        kernelId: JupyterKernelId,
        notebookPath: Path,
        notebookVirtualFile: BackedNotebookVirtualFile?,
    ): KotlinKernelRunnableHandler {
        val options = KotlinNotebookAttachedModeOptions.getInstance(project)
        val host = options.host
        val ports = options.getKernelPorts()

        val kernelConfig = createClientKotlinKernelConfig(
            host,
            ports,
            DEFAULT_SPRING_SIGNATURE_KEY,
        )

        return AttachedKernelProcessHandler(
            project, kernelId, notebookPath, notebookVirtualFile, kernelConfig
        ).apply {
            addBaseKernelListener(MyListener)
        }
    }

    private object MyListener : KotlinKernelListener {
        override fun kernelInfoReplyReceived(event: KernelInfoReplyReceivedEvent) {
            val kernel = event.source
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