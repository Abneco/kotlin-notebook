// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.attached

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.kotlinx.jupyter.messaging.KernelInfoReplyMetadata
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KernelInfoReplyReceivedEvent
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelListener
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.ModeAwareKernelRunnableFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.asRawMessage
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookSessionRunMode
import org.jetbrains.kotlinx.jupyter.plugin.util.jsonConfig
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
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
        return AttachedKernelProcessHandler(
            project, kernelId, notebookPath, notebookVirtualFile,
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

            val classpathInfo = EvaluatedSnippetMetadata(
                newClasspath = replyMetadata.classpath
            )

            compilerService.addCompiledSnippet(classpathInfo, null)
        }
    }
}