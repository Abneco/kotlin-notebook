// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.creating

import com.intellij.codeInsight.folding.impl.FoldingUpdate
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.data.input.JupyterDataInputSettings
import com.intellij.jupyter.core.jupyter.editor.JupyterEditorCustomizer
import com.intellij.jupyter.core.jupyter.helper.isJupyter
import com.intellij.kotlin.jupyter.core.editor.hack.editor.NotebookEditorCreatedListener
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.reactOnThemeChangedEvent
import com.intellij.kotlin.jupyter.core.editor.typing.NotebookCaretListener
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.settings.registryFlag
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async

private val inputDataCellsEnabledInKotlinNotebook by registryFlag("kotlin.notebook.data.input.cells.enabled", false)

class KotlinJupyterEditorCustomizer : JupyterEditorCustomizer {
    override fun onEditorCreated(project: Project, textEditor: TextEditor, virtualFile: BackedNotebookVirtualFile) {
        if (!virtualFile.file.isKotlinNotebook) return

        val options = KotlinNotebookApplicationOptions.get()
        textEditor.putUserData(FoldingUpdate.INJECTED_CODE_FOLDING_ENABLED, options.shouldShowFoldings)

        val editor = textEditor.editor

        if (editor.isJupyter) {
            if (!inputDataCellsEnabledInKotlinNotebook) {
                JupyterDataInputSettings.disableInputCellsForEditor(editor)
            }

            val compilerService = JupyterCompilerService.getInstance(project)
            val parentDisposable: Disposable = (editor as? EditorImpl)?.disposable ?: compilerService
            KotlinNotebookPluginScope.getForProject(project).async {
                // do not init on edt
                project.messageBus.syncPublisher(NotebookEditorCreatedListener.TOPIC).editorCreated(
                    editor,
                    virtualFile
                )
                readAction {
                    editor.caretModel.addCaretListener(
                        NotebookCaretListener(project, virtualFile, editor, parentDisposable),
                        parentDisposable
                    )
                }
            }
            ApplicationManager.getApplication().messageBus.connect(parentDisposable)
                .subscribe(EditorColorsManager.TOPIC,
                           EditorColorsListener {
                               virtualFile.reactOnThemeChangedEvent(project)
                           })
        }
    }
}
