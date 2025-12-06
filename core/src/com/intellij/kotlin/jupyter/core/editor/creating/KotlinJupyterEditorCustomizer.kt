// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.creating

import com.intellij.codeInsight.folding.impl.FoldingUpdate
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.data.input.JupyterDataInputSettings
import com.intellij.jupyter.core.jupyter.editor.JupyterEditorCustomizer
import com.intellij.jupyter.core.jupyter.editor.JupyterFileEditor
import com.intellij.jupyter.core.jupyter.helper.isJupyter
import com.intellij.kotlin.jupyter.core.editor.highlighting.NotebookHighlightingService
import com.intellij.kotlin.jupyter.core.editor.highlighting.editor.NotebookEditorCreatedListener
import com.intellij.kotlin.jupyter.core.editor.highlighting.utils.reactOnThemeChangedEvent
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.settings.registryFlag
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async

private val inputDataCellsEnabledInKotlinNotebook by registryFlag("kotlin.notebook.data.input.cells.enabled", false)

class KotlinJupyterEditorCustomizer : JupyterEditorCustomizer {
    override fun onEditorCreated(project: Project, jupyterFileEditor: JupyterFileEditor, virtualFile: BackedNotebookVirtualFile) {
        if (!virtualFile.file.isKotlinNotebook) return

        val options = KotlinNotebookApplicationOptions.get()
        jupyterFileEditor.putUserData(FoldingUpdate.INJECTED_CODE_FOLDING_ENABLED, options.shouldShowFoldings)

        val editor = jupyterFileEditor.editor

        if (editor.isJupyter) {
            if (!inputDataCellsEnabledInKotlinNotebook) {
                JupyterDataInputSettings.disableInputCellsForEditor(editor)
            }

            val highlightingService = NotebookHighlightingService.getForFile(project, virtualFile)
            val parentDisposable: Disposable = (editor as? EditorImpl)?.disposable ?: highlightingService
            KotlinNotebookPluginScope.getForProject(project).async {
                // do not init on edt
                project.messageBus.syncPublisher(NotebookEditorCreatedListener.TOPIC).editorCreated(
                    editor,
                    virtualFile
                )
            }
            ApplicationManager.getApplication().messageBus.connect(parentDisposable)
                .subscribe(EditorColorsManager.TOPIC,
                           EditorColorsListener {
                               virtualFile.reactOnThemeChangedEvent(project)
                           })
        }
    }
}
