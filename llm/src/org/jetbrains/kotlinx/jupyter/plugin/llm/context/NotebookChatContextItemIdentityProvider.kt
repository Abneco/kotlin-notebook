// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.context

import com.intellij.ml.llm.core.chat.context.ChatContextItemIdentityExtension
import kotlin.reflect.KClass

class NotebookChatContextItemIdentityProvider : ChatContextItemIdentityExtension {
    override fun mapIdentityToClass(): Map<String, KClass<*>> {
        return mapOf(
            KotlinNotebookChatContextProvider::class.qualifiedName!! to KotlinNotebookChatContextProvider::class
        )
    }
}