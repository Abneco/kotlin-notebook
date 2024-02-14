// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.chat

import com.intellij.ml.llm.smartChat.endpoints.SmartChatEndpoint
import com.intellij.ml.llm.smartChat.endpoints.SmartChatEndpointProvider
import org.jetbrains.kotlinx.jupyter.plugin.llm.chat.endpoints.NotebookCurrentSessionDescriberEndpoint
import org.jetbrains.kotlinx.jupyter.plugin.llm.chat.endpoints.NotebookCurrentVariablesAccessorEndpoint
import org.jetbrains.kotlinx.jupyter.plugin.llm.chat.endpoints.NotebookVariableSummaryEndpoint
import org.jetbrains.kotlinx.jupyter.plugin.llm.chat.endpoints.libs.NotebookDataFrameDescriberEndpoint

class KotlinNotebookChatEndpointProviders : SmartChatEndpointProvider() {
    override fun getEndpoints(): List<SmartChatEndpoint> {
        return listOf(
            NotebookCurrentVariablesAccessorEndpoint(),
            NotebookCurrentSessionDescriberEndpoint(),
            NotebookVariableSummaryEndpoint(),
            NotebookDataFrameDescriberEndpoint()
        )
    }
}