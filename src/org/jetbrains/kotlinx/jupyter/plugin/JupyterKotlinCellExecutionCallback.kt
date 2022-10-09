package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.configurationStore.runAsWriteActionIfNeeded
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookReferenceFinder.CELL_CLASS_NAME
import org.jetbrains.kotlinx.jupyter.plugin.util.deserialize
import org.jetbrains.kotlinx.jupyter.plugin.util.logListWarn
import org.jetbrains.plugins.notebooks.core.impl.file.assertBackedNotebook
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterInputRequestMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessageChannel
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterStatusMessage
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterNotebook
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import kotlin.system.measureTimeMillis

/**
 * Methods of [JupyterKotlinCellExecutionCallback] are triggered on
 * corresponding actions performed with the cells in Jupyter notebook.
 *
 * Note that this is not the only callback triggered for cell actions.
 * Jupyter plugin has its own callback which is responsible for rendering,
 * outputs updating and so on. This callback should be used only for
 * language-specific features. If you want to change rendering or other
 * language-agnostic features, contribute to the Jupyter plugin directly.
 */
class JupyterKotlinCellExecutionCallback(
    private val project: Project,
    private val virtualFile: VirtualFile, // backed
    private val psiCell: JupyterPsiCell,
    private val cellSource: String,
) : JupyterExecutionCallback {

    init {
      assertBackedNotebook(virtualFile)
    }

    override val channel: JupyterMessageChannel
        get() = JupyterMessageChannel.ANY
    override var finalizeCallback = {}

    override fun expire() {
    }

    override fun onCommInfoReply(message: JupyterMessage) {
    }

    override fun onClearOutput(message: JupyterMessage) {
    }

    override fun onCompleteReply(message: JupyterMessage) {
    }

    override fun onDebugReply(message: JupyterMessage) {
    }

    override fun onDebugEvent(message: JupyterMessage) {
    }

    override fun onDisplayData(message: JupyterMessage) {
    }

    override fun onExecuteInput(message: JupyterMessage) {
    }

    override fun onExecuteReply(message: JupyterMessage) = invokeLater {
        try {
            val snippetMetadataObject = message.getMetadata("eval_metadata") ?: return@invokeLater
            val snippetMetadata: EvaluatedSnippetMetadata
            val deserializationTime = measureTimeMillis {
                snippetMetadata = snippetMetadataObject.deserialize()
            }

            LOG.logListWarn(
                "Cell executed. Deserialization took $deserializationTime ms. New classpath received",
                snippetMetadata.newClasspath
            )

            /**
             * Acquire an instance of [JupyterCompilerPerFileService] for this notebook
             * and pass the metadata we received to it.
             */
            val compilerService = JupyterCompilerService.getForFile(project, virtualFile)
            compilerService.addCompiledSnippet(snippetMetadata, cellSource)

            updateInjectedCellInfo(snippetMetadata)
        } catch (exception: Throwable) {
            LOG.warn("Kotlin execution callback failed", exception)
        } finally {
            finalizeCallback()
        }
    }

    override fun onInputRequest(message: JupyterInputRequestMessage) {
    }

    override fun onInspectReply(message: JupyterMessage) {
    }

    override fun onStatus(message: JupyterStatusMessage) {
    }

    override fun onUpdateOutput(message: JupyterMessage) {
    }

    private fun updateInjectedCellInfo(snippetMetadata: EvaluatedSnippetMetadata) {
        val injectManager = InjectedLanguageManager.getInstance(project)
        val compilerService = JupyterCompilerService.getForFile(project, virtualFile)

        runAsWriteActionIfNeeded { // maybe synchronized
            val properCompiledClass = snippetMetadata.compiledData.sources.lastOrNull()?.fileName?.substringBefore(".kts")
            (injectManager.getInjectedPsiFiles(psiCell)?.firstOrNull()?.first as? PsiFile)
                ?.putUserData(CELL_CLASS_NAME, properCompiledClass)
            (psiCell.parent as? JupyterNotebook)?.psiCellList?.indexOf(psiCell)?.let {
                if (properCompiledClass != null) {
                    compilerService.cellOrdinalToClassName[it] = properCompiledClass
                }
            }
            psiCell.putUserData(CELL_CLASS_NAME, properCompiledClass)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(this::class.java)
    }
}
