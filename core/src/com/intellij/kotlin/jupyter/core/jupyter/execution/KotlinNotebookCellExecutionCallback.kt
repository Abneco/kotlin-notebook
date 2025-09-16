// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.execution

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallback
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.nbformat.JupyterOutputsBase
import com.intellij.jupyter.execution.util.deserialize
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.statistics.fus.KotlinNotebookFeatureUsagesCollector
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.debugInTests
import com.intellij.kotlin.jupyter.core.util.logListInfo
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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
) : JupyterExecutionCallback {
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
                Disposer.dispose(this@KotlinNotebookCellExecutionCallback)
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
            } else {
                LOG.debugInTests { "No snippet metadata found for message $message" }
            }

            KotlinNotebookFeatureUsagesCollector.registerCellExecuted(
                project,
                message,
                executionFinishedMs - executionStartedMs,
                snippetMetadata ?: EvaluatedSnippetMetadata.EMPTY
            )

            snippetMetadata
        }
        unregisterCallback()

        if (snippetMetadata != null) {
            JupyterCompilerService.getForFile(project, virtualFile)
                .addCompiledSnippet(snippetMetadata, psiCell)
        }
    }

    override fun onUpdateOutput(message: JupyterMessage) {
        val output = JupyterOutputsBase.fromMessage(message) ?: return
        KotlinNotebookFeatureUsagesCollector.registerOutputUpdated(project, output)
    }

    private fun unregisterCallback() {
        kotlinNotebookCellExecutionCallbackFactory.unregisterCallback(project, virtualFile, index)
    }


    companion object {
        private val LOG = notebookLogger()
    }
}
