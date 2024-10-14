// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.execution

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallbackAdapter
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageChannel
import com.intellij.jupyter.core.jupyter.nbformat.JupyterOutputsBase
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingService
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.statistics.fus.KotlinNotebookFeatureUsagesCollector
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.deserialize
import com.intellij.kotlin.jupyter.core.util.logListInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import kotlin.system.measureTimeMillis

/**
 * Methods of [KotlinNotebookCellExecutionCallback] are triggered on
 * corresponding actions performed with the cells in Jupyter notebook.
 *
 * Note that this is not the only callback triggered for cell actions.
 * Jupyter plugin has its own callback which is responsible for rendering,
 * outputs, updating and so on. This callback should be used only for
 * language-specific features. If you want to change rendering or other
 * language-agnostic features, contribute to the Jupyter plugin directly.
 */
class KotlinNotebookCellExecutionCallback(
  private val project: Project,
  private val virtualFile: BackedNotebookVirtualFile,
  private val psiCell: JupyterPsiCell?,
  private val index: Int,
  private val executionStartedMs: Long,
) : JupyterExecutionCallbackAdapter() {
    override val channel: JupyterMessageChannel
        get() = JupyterMessageChannel.ANY
    override var finalizeCallback = {}

    override fun expire() {
        unregisterCallback()
    }

    override fun onExecuteReply(message: JupyterMessage) {
        KotlinNotebookPluginScope.getForProject(project).async {
            try {
                onExecuteReplyImpl(message)
            } catch (e: Throwable) {
                if (e is ProcessCanceledException) {
                    throw e
                }
                LOG.warn("Kotlin execution callback failed", e)
            } finally {
                finalizeCallback()
            }
        }
    }

    private fun onExecuteReplyImpl(message: JupyterMessage) {
        val executionFinishedMs = System.currentTimeMillis()
        val snippetMetadata = message.getMetadata("eval_metadata").let { metadataObject ->
            var snippetMetadata: EvaluatedSnippetMetadata? = null

            if (metadataObject != null) {
                val deserializationTime = measureTimeMillis {
                    snippetMetadata = metadataObject.deserialize()
                }
                LOG.logListInfo(
                    "Cell executed. Deserialization took $deserializationTime ms. New classpath received",
                    snippetMetadata?.newClasspath.orEmpty()
                )
            }

            KotlinNotebookFeatureUsagesCollector.registerCellExecuted(
                project,
                message,
                executionFinishedMs - executionStartedMs,
                snippetMetadata ?: EvaluatedSnippetMetadata.EMPTY
            )

            snippetMetadata
        }
        val metadataIsPresent = snippetMetadata != null
        unregisterCallback(!metadataIsPresent)

        if (metadataIsPresent) {
            /**
             * Acquire an instance of [JupyterCompilerPerFileService] for this notebook
             * and pass the metadata we received to it.
             */
            val compilerService = JupyterCompilerService.getForFile(project, virtualFile)
            compilerService.addCompiledSnippet(snippetMetadata, psiCell)
        } else {
            NotebookHighlightingService.getForFile(project, virtualFile)
                .dataController.notebookDocumentStructureNontrivialChanged.compareAndSet(false, true)
        }
    }

    override fun onUpdateOutput(message: JupyterMessage) {
        val output = JupyterOutputsBase.fromMessage(message) ?: return
        KotlinNotebookFeatureUsagesCollector.registerOutputUpdated(project, output)
    }

    private fun unregisterCallback(snippetMetadataIsEmpty: Boolean = false) {
        val factory = KotlinNotebookCellExecutionCallbackFactory.getInstance()
        factory.unregisterCallback(project, virtualFile, index, snippetMetadataIsEmpty)
    }


    companion object {
        private val LOG = Logger.getInstance(this::class.java)
    }
}
