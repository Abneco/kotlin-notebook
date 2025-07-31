// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport.listeners

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.util.messages.Topic

/**
 * Way to be informed when scripts are updated in the particular [BackedNotebookVirtualFile]
 * Listener is invoked once CompilationConfiguration for all cells in [BackedNotebookVirtualFile] is updated.
 *
 * @see com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerPerFileService
 */
fun interface NotebookScriptsStateListener {
    enum class UpdateState {
        /**
         * Update is completed for this notebook.
         */
        COMPLETE,
        /**
         * Notebook requires update.
         */
        NEEDS_UPDATE,
        /**
         * Update is in progress
         */
        PENDING,
        /**
         * Update is skipped because it is not required.
         */
        SKIPPED
    }

    /**
     * Notifies that the script configuration within the specified [BackedNotebookVirtualFile]
     * has been updated.
     */
    fun scriptsConfigurationUpdated(
        file: BackedNotebookVirtualFile,
        updateState: UpdateState,
    )

    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<NotebookScriptsStateListener> = Topic(
            NotebookScriptsStateListener::class.java,
            Topic.BroadcastDirection.NONE
        )

        val UpdateState.isIncomplete: Boolean
            get() = this == UpdateState.NEEDS_UPDATE
    }
}
