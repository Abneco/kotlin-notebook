// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.editor

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.openapi.editor.Editor
import com.intellij.util.messages.Topic
import java.util.EventListener

fun interface NotebookEditorCreatedListener : EventListener {
    fun editorCreated(editor: Editor, notebookVirtualFile: BackedNotebookVirtualFile)

    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<NotebookEditorCreatedListener> = Topic(
          NotebookEditorCreatedListener::class.java,
          Topic.BroadcastDirection.NONE
        )
    }
}