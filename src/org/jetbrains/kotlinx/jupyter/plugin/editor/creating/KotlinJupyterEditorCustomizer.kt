// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.creating

import com.intellij.codeInsight.folding.impl.FoldingUpdate
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util.reactOnThemeChangedEvent
import org.jetbrains.kotlinx.jupyter.plugin.editor.typing.NotebookCaretListener
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptions
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.editor.JupyterEditorCustomizer
import com.intellij.jupyter.core.jupyter.editor.isJupyter

class KotlinJupyterEditorCustomizer : JupyterEditorCustomizer {
    override fun onEditorCreated(project: Project, textEditor: TextEditor, virtualFile: BackedNotebookVirtualFile) {
        if (!virtualFile.file.isKotlinNotebook) return

        val options = KotlinNotebookApplicationOptions.get()
        textEditor.putUserData(FoldingUpdate.INJECTED_CODE_FOLDING_ENABLED, options.shouldShowFoldings)

        val editor = textEditor.editor

        if (editor.isJupyter) {
            val compilerService = JupyterCompilerService.getInstance(project)
            val parentDisposable: Disposable = (editor as? EditorImpl)?.disposable ?: compilerService
            editor.caretModel.addCaretListener(
                NotebookCaretListener(project, virtualFile, editor, parentDisposable),
                parentDisposable
            )
            ApplicationManager.getApplication().messageBus.connect(parentDisposable)
                .subscribe(EditorColorsManager.TOPIC,
                           EditorColorsListener {
                               virtualFile.reactOnThemeChangedEvent(project)
                           })
        }
    }
}
