// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.actions

import com.intellij.codeInsight.folding.impl.FoldingUpdate
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.editor.NotebookCaretListener
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.reactOnThemeChangedEvent
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterEditorCustomizer
import org.jetbrains.plugins.notebooks.jupyter.editor.isJupyter

class KotlinJupyterEditorCustomizer : JupyterEditorCustomizer {
    override fun onEditorCreated(project: Project, editor: Editor, virtualFile: BackedNotebookVirtualFile) {
        if (!virtualFile.file.isKotlinNotebook) return

        editor.putUserData(FoldingUpdate.INJECTED_CODE_FOLDING_ENABLED, false)
        if (editor.isJupyter) {
            val compilerService = JupyterCompilerService.getForFile(project, virtualFile)
            editor.caretModel.addCaretListener(NotebookCaretListener(project, virtualFile, editor), compilerService)
            ApplicationManager.getApplication().messageBus.connect()
                .subscribe(EditorColorsManager.TOPIC,
                           EditorColorsListener {
                               editor.document.reactOnThemeChangedEvent()
                           })
        }
    }
}
