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
     * Fires after the kernel is started, the session is initialized.
     * Considers if this session was restarted or not.
     */
    fun sessionStarted(virtualFile: BackedNotebookVirtualFile, isAfterRestart: Boolean = false) = Unit
}