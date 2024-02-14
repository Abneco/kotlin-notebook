// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.chat.functions

import ai.grazie.model.llm.chat.function.LLMFunction
import com.intellij.ml.llm.core.chat.messages.impl.FunctionCallResult
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

interface NotebookLLMFunction <T> {
    val name: String

    val LLMFunction: LLMFunction

    suspend fun invokeWithParameters(project: Project, virtualFile: BackedNotebookVirtualFile, param: T? = null): FunctionCallResult
}