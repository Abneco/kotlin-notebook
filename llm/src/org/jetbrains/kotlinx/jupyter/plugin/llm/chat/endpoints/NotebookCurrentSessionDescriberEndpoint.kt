// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.chat.endpoints

import ai.grazie.model.llm.chat.function.LLMFunction
import com.intellij.ml.llm.core.chat.messages.impl.FunctionCallResult
import com.intellij.ml.llm.core.chat.session.ChatSessionStorage
import com.intellij.ml.llm.smartChat.endpoints.SmartChatEndpoint
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.llm.chat.functions.NotebookSessionAnalyzerFunction
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.CommonReusableResultFunctions
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.findVirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.retrieveCurrentBackedNotebookFileOrNull
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.common.toBackedKotlinNotebookOrNull
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.data.SessionSummarizationFunctionArguments
import org.jetbrains.kotlinx.jupyter.plugin.llm.util.data.parseFromJson

class NotebookCurrentSessionDescriberEndpoint : SmartChatEndpoint {
    override suspend fun isAvailable(project: Project, sourceAction: ChatSessionStorage.SourceAction): Boolean {
        return true
    }

    override val llmFunction: LLMFunction
        get() = NotebookSessionAnalyzerFunction.LLMFunction


    override suspend fun call(serializedArguments: String, project: Project): FunctionCallResult {
        val targetFile = serializedArguments.parseFromJson<SessionSummarizationFunctionArguments>().targetFile
        val notebook = if (targetFile.isNullOrEmpty()) {
            project.retrieveCurrentBackedNotebookFileOrNull()
        } else project.findVirtualFile(targetFile)?.toBackedKotlinNotebookOrNull()
        if (notebook == null) {
            return CommonReusableResultFunctions.fileIsNotKtNotebookError
        }

        return NotebookSessionAnalyzerFunction.invokeWithParameters(project, notebook)
    }

    override fun getPresentationString(): String {
        return "Analyzing current Kotlin Notebook session..."
    }
}