// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor

import com.intellij.codeInsight.folding.impl.FoldingUpdate
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.reactOnThemeChangedEvent
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookPerFileSettingsCache
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterEditorCustomizer
import org.jetbrains.plugins.notebooks.jupyter.editor.isJupyter

class KotlinJupyterEditorCustomizer : JupyterEditorCustomizer {
    override fun onEditorCreated(project: Project, editor: Editor, virtualFile: BackedNotebookVirtualFile) {
        val file = virtualFile.file
        if (!file.isKotlinNotebook) return

        editor.putUserData(FoldingUpdate.INJECTED_CODE_FOLDING_ENABLED, false)
        if (editor.isJupyter) {
            JupyterKtScriptingSupport.update(project)
            KotlinNotebookPerFileSettingsCache.getInstance(project).notebookEditorCreated(file)

            val compilerService = JupyterCompilerService.getInstance(project)
            val parentDisposable: Disposable = (editor as? EditorImpl)?.disposable ?: compilerService
            editor.caretModel.addCaretListener(
                NotebookCaretListener(project, virtualFile, editor, parentDisposable),
                parentDisposable
            )
            ApplicationManager.getApplication().messageBus.connect(parentDisposable)
                .subscribe(EditorColorsManager.TOPIC,
                           EditorColorsListener {
                               editor.document.reactOnThemeChangedEvent(project, file)
                           })
        }
    }
}
