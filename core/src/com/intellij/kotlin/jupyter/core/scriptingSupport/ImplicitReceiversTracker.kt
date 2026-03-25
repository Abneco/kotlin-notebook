// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.script.experimental.api.KotlinType

/**
 * Tracks implicit receiver classes loaded from executed notebook snippets.
 *
 * Contract: "at-least-once consumption across update cycles" — a snippet added concurrently
 * during [consumeReadySnippets] will survive to the next cycle and trigger a re-update.
 */
internal class ImplicitReceiversTracker : ImplicitListsConfigurationUpdater {
    private val pendingSnippets = ConcurrentLinkedQueue<ClassPathSnippetsLoadedData>()

    val lastLoadedTypeOrNull: KotlinType?
        get() = pendingSnippets.lastOrNull()?.snippetTypes?.lastOrNull()

    override val hasPendingSnippets: Boolean
        get() = pendingSnippets.isNotEmpty()

    override fun addLoadedSnippet(snippetData: ClassPathSnippetsLoadedData) {
        pendingSnippets.add(snippetData)
    }

    /**
     * Returns snippets whose types are present in indexes, and removes them from the pending queue.
     *
     * @param typeFilter verifies that all types in a snippet are present in indexes
     */
    suspend fun consumeReadySnippets(
        typeFilter: suspend (List<KotlinType>) -> Collection<KotlinType>
    ): List<ClassPathSnippetsLoadedData> {
        if (pendingSnippets.isEmpty()) return emptyList()

        val ready = pendingSnippets.toList().filter { snippet ->
            val presentTypes = typeFilter(snippet.snippetTypes)
            presentTypes.containsAll(snippet.snippetTypes)
        }

        pendingSnippets.removeAll(ready.toSet())
        return ready
    }

    fun clear() {
        pendingSnippets.clear()
    }
}
