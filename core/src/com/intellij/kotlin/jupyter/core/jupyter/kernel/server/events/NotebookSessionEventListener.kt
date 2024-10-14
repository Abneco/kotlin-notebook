// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.events

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.util.messages.Topic
import java.util.*

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