// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.chat.endpoints

import ai.grazie.model.llm.chat.function.LLMFunction
import com.intellij.ml.llm.core.chat.messages.impl.FunctionCallResult
import com.intellij.ml.llm.core.chat.session.ChatSessionStorage
import com.intellij.ml.llm.smartChat.endpoints.SmartChatEndpoint
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.llm.chat.functions.NotebookVariableExplainerFunction
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.CommonReusableResultFunctions
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.retrieveCurrentBackedNotebookFileOrNull
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.data.VariableDescriberFunctionArguments
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.data.parseFromJson

class NotebookVariableSummaryEndpoint : SmartChatEndpoint {
    override suspend fun isAvailable(project: Project, sourceAction: ChatSessionStorage.SourceAction): Boolean {
        return true
    }

    override val llmFunction: LLMFunction
        get() = NotebookVariableExplainerFunction.LLMFunction

    override suspend fun call(serializedArguments: String, project: Project): FunctionCallResult {
        val notebookFile = project.retrieveCurrentBackedNotebookFileOrNull()
        if (notebookFile == null) {
            return CommonReusableResultFunctions.fileIsNotKtNotebookError
        }
        val arguments = serializedArguments.parseFromJson<VariableDescriberFunctionArguments>()

        return NotebookVariableExplainerFunction.invokeWithParameters(
            project, notebookFile, arguments
        )
    }

    override fun getPresentationString(): String {
        return "Acquiring information about the variable..."
    }
}