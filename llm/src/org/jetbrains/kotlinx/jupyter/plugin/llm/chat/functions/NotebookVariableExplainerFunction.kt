// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.chat.functions

import ai.grazie.model.llm.chat.function.LLMFunction
import com.intellij.debugger.ui.tree.FieldDescriptor
import com.intellij.ml.llm.core.chat.messages.impl.FunctionCallResult
import com.intellij.ml.llm.smartChat.endpoints.search.parametersFromJson
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.NotebookVariableDescriptorPositionResolver
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.retrieveVariableValue
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.CommonReusableResultFunctions
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.data.VariableDescriberFunctionArguments
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

object NotebookVariableExplainerFunction : NotebookLLMFunction<VariableDescriberFunctionArguments> {
    override val name: String = "describe_variable_inside_notebook_session"

    override val LLMFunction: LLMFunction
        get() = LLMFunction(
            name = name,
            description = """
               Provide broad information about particular variable inside the Notebook.
               Variable must be declared. 
               You MUST represent information in rich text. 
               You MUST show declaration code, if there is such.
               If there is information present, consider any code as a Kotlin code. 
            """.trimIndent(),
            parameters = parametersFromJson(
                """
              {
                  "type": "object",
                  "properties": {
                      "variableName": {
                         "type": "string",
                         "description": "Name of the declared variable"
                      }
                  },
                  "required": ["variableName"]
              }
              """.trimIndent()
            )
        )

    override suspend fun invokeWithParameters(project: Project, virtualFile: BackedNotebookVirtualFile, param: VariableDescriberFunctionArguments?): FunctionCallResult {
        if (param == null) {
            return CommonReusableResultFunctions.variableDataIsNotFound
        }
        val variable = param.variableName
        val javaValue = virtualFile.retrieveVariableValue(project, variable)
        if (javaValue == null) {
            return CommonReusableResultFunctions.variableDataIsNotFound
        }
        val descriptor = javaValue.descriptor as FieldDescriptor

        val declaration = readAction {
            NotebookVariableDescriptorPositionResolver
                .resolveTo(project, virtualFile.file, descriptor)?.elementAt?.parent?.text
        }

        return buildString {
            with(javaValue) {
                appendLine("Variable name: $name")
                appendLine("Variable value: ${valueText}")
                appendLine("Containing compiled snippet class: ${descriptor.`object`.referenceType().name()}")
                //appendLine("Value type: ${valueText}")
                appendLine("Declaration from which it was built: ${declaration}")
            }
        }.let {
            FunctionCallResult.Success(it)
        }
    }
}