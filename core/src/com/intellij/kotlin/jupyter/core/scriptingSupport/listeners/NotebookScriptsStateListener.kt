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
fun interface NotebookScriptsStateListener {
    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<NotebookScriptsStateListener> = Topic(NotebookScriptsStateListener::class.java, Topic.BroadcastDirection.NONE)

        val UpdateState.isComplete: Boolean
            get() = this == UpdateState.COMPLETE

        val UpdateState.isIncomplete: Boolean
            get() = this == UpdateState.INCOMPLETE
    }

    enum class UpdateState {
        COMPLETE,
        INCOMPLETE
    }

    // todo: comments
    // add enum to function
    fun scriptsConfigurationUpdated(file: BackedNotebookVirtualFile, updateState: UpdateState)

}
