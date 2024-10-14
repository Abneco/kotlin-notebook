// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport.listeners

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.util.messages.Topic

/**
 * Way to be informed when scripts are updated in the particular [BackedNotebookVirtualFile]
 * Listener is invoked once CompilationConfiguration for all cells in [BackedNotebookVirtualFile] is updated.
 *
 * @see JupyterCompilerPerFileService
 */
fun interface NotebookCodeSnippetsChangeListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<NotebookCodeSnippetsChangeListener> = Topic(NotebookCodeSnippetsChangeListener::class.java, Topic.BroadcastDirection.NONE)
    }

    fun scriptsClassesChanged(file: BackedNotebookVirtualFile)
}
