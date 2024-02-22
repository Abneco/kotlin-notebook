// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.events

import com.intellij.util.messages.Topic
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

interface NotebookSessionEventListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<NotebookSessionEventListener> = Topic(NotebookSessionEventListener::class.java, Topic.BroadcastDirection.NONE)
    }

    fun kernelRestarted(virtualFile: BackedNotebookVirtualFile) = Unit

    fun sessionRestarted(virtualFile: BackedNotebookVirtualFile) = Unit
}