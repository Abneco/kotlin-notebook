package org.jetbrains.kotlinx.jupyter.plugin

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScript
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedCompiledScriptsData
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterAdditionalCellExecutionCallback
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterInputRequestMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterStatusMessage
import java.io.File

class JupyterKotlinCellExecutionCallback : JupyterAdditionalCellExecutionCallback {
    override fun onClearOutput(message: JupyterMessage, project: Project, virtualFile: NotebookVirtualFile) {
    }

    override fun onCompleteReply(message: JupyterMessage, project: Project, virtualFile: NotebookVirtualFile) {
    }

    override fun onDisplayData(message: JupyterMessage, project: Project, virtualFile: NotebookVirtualFile) {
    }

    override fun onExecuteInput(message: JupyterMessage, project: Project, virtualFile: NotebookVirtualFile) {
    }

    override fun onExecuteReply(message: JupyterMessage, project: Project, virtualFile: NotebookVirtualFile) {
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

        val newClasspath: List<File> = (message.getMetadata("new_classpath") as? ArrayNode)?.let { classpathArray ->
            val result = mutableListOf<File>()
            classpathArray.forEach { node ->
                if (node !is TextNode) return
                result.add(File(node.textValue()))
            }
            result
        }.orEmpty()

        val compilerService = JupyterCompilerService.getForFile(project, virtualFile)
        compilerService.addCompiledSnippet(compiledData, newClasspath)
    }

    override fun onInputRequest(message: JupyterInputRequestMessage, project: Project, virtualFile: NotebookVirtualFile) {
    }

    override fun onInspectReply(message: JupyterMessage, project: Project, virtualFile: NotebookVirtualFile) {
    }

    override fun onStatus(message: JupyterStatusMessage, project: Project, virtualFile: NotebookVirtualFile) {
    }

    override fun onUpdateOutput(message: JupyterMessage, project: Project, virtualFile: NotebookVirtualFile) {
    }
}
