// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.typing.daemon

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.kotlin.jupyter.core.editor.highlighting.events.NotebookDaemonFinishedEvent
import com.intellij.kotlin.jupyter.core.editor.typing.state.NotebookCaretStateProcessor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor

internal class NotebookHighlightingDaemonListener(
    private val stateProcessor: NotebookCaretStateProcessor
) : DaemonListener {
    override fun daemonFinished(fileEditors: MutableCollection<out FileEditor>) {
        fileEditors.firstOrNull { (it as? TextEditor)?.editor == stateProcessor.editor }?.let {
            stateProcessor.onEventHappened(NotebookDaemonFinishedEvent)
        }
    }
}