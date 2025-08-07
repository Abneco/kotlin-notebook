// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.document.topic

import com.intellij.openapi.editor.Editor
import com.intellij.util.messages.Topic

/**
 * Listener for notifications about cell structure in the document.
 * Note that there is a guarantee that the underlying 'editor.document' contains a notebook file.
 */
fun interface DocumentCellsStructureChangedListener {
    fun cellsChanged(editor: Editor, cellFocus: Int)

    companion object {
        @JvmField
        val TOPIC: Topic<DocumentCellsStructureChangedListener> = Topic(
            DocumentCellsStructureChangedListener::class.java,
            Topic.BroadcastDirection.NONE
        )
    }
}