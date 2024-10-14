// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.events

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

fun interface JupyterSessionVerifiedListener {
    companion object {
        /**
         * Notifies that the session is verified and ready to be used.
         * This information to be propagated in [NotebookSessionEventListener]
         */
        @Topic.AppLevel
        val TOPIC: Topic<JupyterSessionVerifiedListener> = Topic(JupyterSessionVerifiedListener::class.java, Topic.BroadcastDirection.NONE)
    }

    fun verifiedSessionStarting(project: Project, virtualFile: BackedNotebookVirtualFile)
}