package org.jetbrains.kotlinx.jupyter.plugin

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScript
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterExecutionCallback
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterInputRequestMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessageChannel
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterStatusMessage
import java.io.File

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
    private val virtualFile: NotebookVirtualFile
) : JupyterExecutionCallback {
    override val channel: JupyterMessageChannel
        get() = JupyterMessageChannel.ANY
    override var finalizeCallback = {}

    override fun expire() {
    }

    override fun onClearOutput(message: JupyterMessage) {
    }

    override fun onCompleteReply(message: JupyterMessage) {
    }

    override fun onDisplayData(message: JupyterMessage) {
    }

    override fun onExecuteInput(message: JupyterMessage) {
    }

    override fun onExecuteReply(message: JupyterMessage) {
        /**
         * All scripts compiled are contained in the form of base64-encoded strings
         * in the reply metadata. We need to turn them into [SerializedCompiledScriptsData] first
         */
        val compiledDataJson = message.getMetadata("compiled_data") as? ObjectNode ?: return
        val scriptsArray = compiledDataJson[SerializedCompiledScriptsData::scripts.name] as? ArrayNode ?: return
        val compiledDataList = mutableListOf<SerializedCompiledScript>()
        scriptsArray.iterator().forEach { node ->
            if (node !is ObjectNode) return
            val fileName = node[SerializedCompiledScript::fileName.name] as? TextNode ?: return
            val base64Data = node[SerializedCompiledScript::data.name] as? TextNode ?: return
            val compiledScript = SerializedCompiledScript(fileName.textValue(), base64Data.textValue())
            compiledDataList.add(compiledScript)
        }
        val compiledData = SerializedCompiledScriptsData(compiledDataList)

        /**
         * New classpath resolved from [jupyter.kotlin.DependsOn] annotation and from
         * %use magic is also contained in the metadata. Extract it too
         */
        val newClasspath: List<File> = (message.getMetadata("new_classpath") as? ArrayNode)?.let { classpathArray ->
            val result = mutableListOf<File>()
            classpathArray.forEach { node ->
                if (node !is TextNode) return
                result.add(File(node.textValue()))
            }
            result
        }.orEmpty()

        /**
         * Acquire an instance of [JupyterCompilerPerFileService] for this notebook
         * and pass the metadata we received to it.
         */
        val compilerService = JupyterCompilerService.getForFile(project, virtualFile)
        compilerService.addCompiledSnippet(compiledData, newClasspath)
    }

    override fun onInputRequest(message: JupyterInputRequestMessage) {
    }

    override fun onInspectReply(message: JupyterMessage) {
    }

    override fun onStatus(message: JupyterStatusMessage) {
    }

    override fun onUpdateOutput(message: JupyterMessage) {
    }
}
