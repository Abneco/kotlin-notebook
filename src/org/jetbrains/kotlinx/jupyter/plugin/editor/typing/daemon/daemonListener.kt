// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.typing.daemon

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.DaemonListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingManager
import org.jetbrains.kotlinx.jupyter.plugin.editor.typing.state.NotebookCaretStateProcessor

class NotebookHighlightingDaemonListener(
    project: Project,
    private val notebookHighlightingManager: NotebookHighlightingManager?,
    private val stateProcessor: NotebookCaretStateProcessor
) : DaemonListener {
    private val scriptDefManager = ScriptDefinitionsManager.getInstance(project)

    override fun daemonFinished(fileEditors: MutableCollection<out FileEditor>) {
        fileEditors.firstOrNull { (it as? TextEditor)?.editor == stateProcessor.editor }?.let {
            if (!scriptDefManager.isReady()) {
                return
            }
            stateProcessor.onDaemonFinishEvent()
        }
    }
}