// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.repl.embedded.DefaultInMemoryReplResultsHolder
import org.jetbrains.kotlinx.jupyter.repl.embedded.InMemoryReplResultsHolder
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.NotebookDisplayOutputDataKeyExtractor

/**
 * This class contains a service that tracks the lifecycle of [InMemoryReplResultsHolder]'s.
 *
 * Each [InMemoryReplResultsHolder] should have a lifecycle that matches a Kotlin Notebook
 * session, i.e., the holder should be created when the session is started and be removed
 * when the session is closed.
 *
 * This is a [Service], so we do not need to modify the [NotebookDisplayOutputDataKeyExtractor]
 * interface in a way that expose Kotlin Notebook specific functionality.
 *
 * @see
 */
@Service(Service.Level.PROJECT)
class InMemoryReplResultsHolderService {

    private val holders = HashMap<JupyterNotebookSessionId, InMemoryReplResultsHolder>()

    /**
     * Get an existing or create a new [InMemoryReplResultsHolder] for the given [sessionId].
     */
    @Synchronized
    fun getOrCreateHolder(sessionId: JupyterNotebookSessionId): InMemoryReplResultsHolder {
        return holders.getOrPut(sessionId) { DefaultInMemoryReplResultsHolder() }
    }

    /**
     * Return an existing [InMemoryReplResultsHolder] for the [sessionId] or `null` or
     * no holder exists.
     */
    @Synchronized
    fun getHolder(sessionId: JupyterNotebookSessionId): InMemoryReplResultsHolder? {
        return holders[sessionId]
    }

    /**
     * Removes the [InMemoryReplResultsHolder] for the provided [sessionId].
     * This should also make all values tracked being eligible for GC.
     * Returns `true` if a holder was registered for the given session id.
     */
    @Synchronized
    fun removeHolder(sessionId: JupyterNotebookSessionId): Boolean {
        return holders.remove(sessionId) != null
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): InMemoryReplResultsHolderService = project.service()
    }
}
