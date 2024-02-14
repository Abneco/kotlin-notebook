// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.chat.endpoints.libs

import ai.grazie.model.llm.chat.function.LLMFunction
import com.intellij.ml.llm.core.chat.messages.impl.FunctionCallResult
import com.intellij.ml.llm.core.chat.session.ChatSessionStorage
import com.intellij.ml.llm.smartChat.endpoints.SmartChatEndpoint
import com.intellij.ml.llm.smartChat.endpoints.search.parametersFromJson
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.jetbrains.python.debugger.pydev.TableCommandType
import com.jetbrains.python.debugger.pydev.tables.CommandOutputType
import org.jetbrains.kotlinx.jupyter.plugin.debug.variables.NotebookSessionVariablesService
import org.jetbrains.kotlinx.jupyter.plugin.llm.chat.functions.NotebookVariableExplainerFunction
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.CommonReusableResultFunctions
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.retrieveCurrentBackedNotebookFileOrNull
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.retrieveCurrentEditor
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.createDataFrameChatAttachmentText
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.data.VariableDescriberFunctionArguments
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.data.parseFromJson
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.tables.JupyterTableCommandExecutor
import org.jetbrains.plugins.notebooks.tables.api.DSTableDataType
import org.jetbrains.plugins.notebooks.tables.api.DSTableText
import org.jetbrains.plugins.notebooks.tables.api.getTableDataProvider

class NotebookDataFrameDescriberEndpoint : SmartChatEndpoint {
    override suspend fun isAvailable(project: Project, sourceAction: ChatSessionStorage.SourceAction): Boolean {
        return true
    }

    override val llmFunction: LLMFunction
        get() = LLMFunction(
            name = "describe_ds_library_dataframe",
            description = """
                Describe current Kotlin DataFrame. 
                Use additional information to state what is containing class of the variable, if possible.
                Print information in pretty manner. 
            """.trimIndent(),
            parameters = parametersFromJson(
                """
              {
                  "type": "object",
                  "properties": {
                      "variableName": {
                         "type": "string",
                         "description": "Name of the variable pointing to the Kotlin dataFrame"
                      }
                  },
                  "required": ["variableName"]
              }
              """.trimIndent()
            )
        )

    override suspend fun call(serializedArguments: String, project: Project): FunctionCallResult {
        val notebook = project.retrieveCurrentBackedNotebookFileOrNull()
        if (notebook == null) {
            return CommonReusableResultFunctions.fileIsNotKtNotebookError
        }

        val arguments = serializedArguments.parseFromJson<VariableDescriberFunctionArguments>()
        val editor = project.retrieveCurrentEditor()
        if (editor == null) {
            return CommonReusableResultFunctions.fileIsNotKtNotebookError
        }

        val describedDataFrame = extractDataFrameInfo(project, notebook, editor, arguments.variableName)

        val additionalInfo = NotebookVariableExplainerFunction
            .invokeWithParameters(project, notebook, arguments).response

        return FunctionCallResult.Success(
            describedDataFrame + additionalInfo,
        )
    }

    override fun getPresentationString(): String {
        return "Analyzing Kotlin DataFrame..."
    }
}


internal fun extractDataFrameInfo(
    project: Project,
    virtualFile: BackedNotebookVirtualFile,
    editor: Editor,
    variable: String
): String {
    try {
        val commandExecutor = JupyterTableCommandExecutor(project, virtualFile, null, editor)
        val outputInfo = commandExecutor.executeCommand(
            "DISPLAY($variable)",
            TableCommandType.DF_INFO, CommandOutputType.DISPLAY
        ).ifEmpty { return "No detailed information about DataFrame schema. Check if it is indeed of DataFrame type." }

        val variableService = NotebookSessionVariablesService.getForFile(project, virtualFile)
        val dfReference = variableService.getVariableValueByNameOrNull(variable) ?: return ""

        val provider = getTableDataProvider(project, DSTableDataType.EXTERNAL, DSTableText(outputInfo, outputInfo))
        val tableData = provider.getTableInfo(commandExecutor, dfReference.name, outputInfo)

        return tableData.createDataFrameChatAttachmentText(dfReference.name)
    } catch (ex: Exception) {
        return "Exception occurred while extracting $variable data, ${ex.message}"
    }
}
