// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.events

import com.intellij.util.messages.Topic
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import java.util.EventListener

/**
 * Interface for being notified about events related to the session lifecycle.
 * Designed to be used across the Kotlin Notebook plugin.
 */
interface NotebookSessionEventListener : EventListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<NotebookSessionEventListener> = Topic(NotebookSessionEventListener::class.java, Topic.BroadcastDirection.NONE)
    }

    /**
     * Fires after kernel is started, session is initialised.
     */
    fun kernelStarted(virtualFile: BackedNotebookVirtualFile) = Unit

    /**
     * Fires after session is restarted.
     */
    fun sessionRestarted(virtualFile: BackedNotebookVirtualFile) = Unit
}