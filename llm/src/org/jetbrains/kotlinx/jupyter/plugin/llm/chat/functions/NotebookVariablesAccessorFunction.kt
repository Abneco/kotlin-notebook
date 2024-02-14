// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.chat.functions

import ai.grazie.model.llm.chat.function.LLMFunction
import com.intellij.debugger.engine.JavaValue
import com.intellij.ml.llm.core.chat.messages.impl.FunctionCallResult
import com.intellij.ml.llm.smartChat.endpoints.search.parametersFromJson
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.retrieveCurrentVariables
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.CommonReusableResultFunctions
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

object NotebookVariablesAccessorFunction : NotebookLLMFunction<Unit> {
    override val name: String
        get() = "describe_current_notebook_session_variables"

    override val LLMFunction: LLMFunction
        get() = LLMFunction(
            name = name,
            description = """
        You MUST provide information about Kotlin Notebook session in the specified file, if any. 
        Always state name of the current Notebook.
        Present variables information in pretty and informative manner line-by-line.
        Suggest the user to refer to Kotlin Notebook toolwindow for more info.
      """.trimIndent(),
            parameters = parametersFromJson("""
              {
                  "type": "object",
                  "properties": {
                  },
                  "required": []
              }
              """.trimIndent())
        )

    override suspend fun invokeWithParameters(project: Project, virtualFile: BackedNotebookVirtualFile, param: Unit?): FunctionCallResult {
        val variables = virtualFile.retrieveCurrentVariables(project)
        if (variables == null || variables.size() == 0) {
            return CommonReusableResultFunctions.variablesAreNotExisting
        }

        val data = buildString {
            appendLine("Current file: $virtualFile")
            appendLine("Following variables are declared:\n")
            (0 until (variables.size())).forEach { i ->
                val value = variables.getValue(i) as? JavaValue
                appendLine("Name: ${variables.getName(i)}, value: $value, type: ${value?.descriptor?.declaredTypeLabel}")
            }
            appendLine()
        }

        return FunctionCallResult.Success(data)
    }
}