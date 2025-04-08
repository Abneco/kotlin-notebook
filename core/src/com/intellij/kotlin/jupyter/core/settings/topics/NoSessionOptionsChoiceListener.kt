// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.topics

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.util.messages.Topic


@Topic.ProjectLevel
val NO_SESSION_OPTIONS_CHOICE_TOPIC: Topic<EmptySessionOptionsChoiceListener> = Topic(EmptySessionOptionsChoiceListener::class.java, Topic.BroadcastDirection.NONE, true)

/**
 * Listener for reflecting on changes made to current selection
 * of notebook dependencies or a target mode,
 * if there is no active [JupyterNotebookSession] for the given [BackedNotebookVirtualFile].
 */
fun interface EmptySessionOptionsChoiceListener {
    fun optionsChanged(notebookFile: BackedNotebookVirtualFile)
}