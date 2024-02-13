package org.jetbrains.kotlinx.jupyter.plugin.llm.context

import com.intellij.ml.llm.core.chat.context.ChatContextItem
import com.intellij.ml.llm.core.chat.context.ChatContextProvider
import com.intellij.ml.llm.core.chat.session.ChatCreationContext
import com.intellij.openapi.project.Project

class KotlinNotebookChatContextProvider : ChatContextProvider {
    override fun isApplicable(project: Project, creationContext: ChatCreationContext): Boolean {
        return true
    }

    override suspend fun collect(project: Project, creationContext: ChatCreationContext): List<ChatContextItem> {
        val qualifiedName = KotlinNotebookChatContextProvider::class.qualifiedName!!
        return listOf(
            ChatContextItem(
                qualifiedName,
                "You can suggest user to see settings panel for Kotlin Notebook, if asked."
            ),
            ChatContextItem(
                qualifiedName,
                "You MUST write examples of DataFrame in Kotlin language, if not asked otherwise."
            ),
            ChatContextItem(
                qualifiedName,
                "If asked about dependencies in current Notebook session, consider context of current top level file"
            ),
            ChatContextItem(
                KotlinNotebookChatContextProvider::class.qualifiedName!!,
                "You MUST not compare Kotlin Notebooks and Python ones."
            )
        )
    }
}