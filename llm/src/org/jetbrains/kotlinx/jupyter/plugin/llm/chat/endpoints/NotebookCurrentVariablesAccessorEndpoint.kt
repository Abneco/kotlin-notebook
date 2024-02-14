// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.chat.endpoints

import ai.grazie.model.llm.chat.function.LLMFunction
import com.intellij.ml.llm.core.chat.messages.impl.FunctionCallResult
import com.intellij.ml.llm.core.chat.session.ChatSessionStorage
import com.intellij.ml.llm.smartChat.endpoints.SmartChatEndpoint
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.llm.chat.functions.NotebookVariablesAccessorFunction
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.CommonReusableResultFunctions.fileIsNotKtNotebookError
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.retrieveCurrentBackedNotebookFileOrNull


class NotebookCurrentVariablesAccessorEndpoint : SmartChatEndpoint {
    companion object {
        const val VARS_ENDPOINT_FUNC_NAME = "describe_current_notebook_session_variables"
    }
    // todo: change to registry flag
    override suspend fun isAvailable(project: Project, sourceAction: ChatSessionStorage.SourceAction): Boolean {
        return true
    }

    override val llmFunction: LLMFunction
        get() = NotebookVariablesAccessorFunction.LLMFunction

    override suspend fun call(serializedArguments: String, project: Project): FunctionCallResult {
        val notebook = project.retrieveCurrentBackedNotebookFileOrNull()
        if (notebook == null) return fileIsNotKtNotebookError

        return NotebookVariablesAccessorFunction.invokeWithParameters(project, notebook)
    }


    override fun getPresentationString(): String {
       return "Describe current Kotlin Notebook session"
    }

}