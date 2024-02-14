// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.chat.functions

import ai.grazie.model.llm.chat.function.LLMFunction
import com.intellij.ml.llm.core.chat.messages.impl.FunctionCallResult
import com.intellij.ml.llm.smartChat.endpoints.search.parametersFromJson
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.createAttachmentText
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.getNotebookSessionData
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

object NotebookSessionAnalyzerFunction : NotebookLLMFunction<Unit>  {
    override val name: String
        get() = "describe_current_notebook_session"

    override val LLMFunction: LLMFunction
        get() = LLMFunction(
            name = name,
            description = """
        You MUST provide information about Kotlin Notebook session in the current opened file, if any.
        Present information in pretty and informative manner. 
        Information shall contain executed classes list as well as dependent libraries from the classpath.
        Mention libraries from the current classpath. 
        List libraries with name of library and it's version, please, don't state simply file names.
      """.trimIndent(),
            parameters = parametersFromJson("""
              {
                  "type": "object",
                  "properties": {
                      "targetFile": {
                         "type": "string",
                         "description": "file name of the notebook for which data about session needs to be shown, or null"
                      }
                  },
                  "required": []
              }
              """.trimIndent())
        )

    override suspend fun invokeWithParameters(project: Project, virtualFile: BackedNotebookVirtualFile, param: Unit?): FunctionCallResult {
        val notebookSessionData = virtualFile.getNotebookSessionData(project)

        val data = notebookSessionData.createAttachmentText(virtualFile, false)

        return FunctionCallResult.Success(data)
    }

}