// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.codeInsight.folding.impl.FoldingUpdate
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.DocumentEx
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook

object KotlinNotebookApplicationOptions {
    private val options: KotlinNotebookApplicationOptionsProvider @Synchronized get() {
        return serviceIfCreated<KotlinNotebookApplicationOptionsProvider>() ?:
            service<KotlinNotebookApplicationOptionsProvider>().apply {
                addListener(object : KotlinNotebookApplicationOptionsProvider.Listener {
                    override fun onShowExecutionCountChanged() {
                        refreshEditors()
                    }

                    override fun onShowFoldings(oldValue: Boolean, newValue: Boolean) {
                        refreshEditors(foldInjected = newValue)
                    }
                }, this)
            }
    }

    private fun refreshEditors(foldInjected: Boolean? = null) {
        EditorFactory.getInstance().allEditors.forEach { editor ->
            if (editor.isKotlinNotebook) {
                if (foldInjected != null) {
                    updateFoldRegions(editor, foldInjected)
                }
                editor.component.repaint()
            }
        }
    }

    private fun updateFoldRegions(editor: Editor, foldInjected: Boolean) {
        val oldValue = editor.getUserData(FoldingUpdate.INJECTED_CODE_FOLDING_ENABLED)
        if (oldValue == foldInjected) return
        editor.putUserData(FoldingUpdate.INJECTED_CODE_FOLDING_ENABLED, foldInjected)

        val project = editor.project ?: return
        val document = editor.document as? DocumentEx ?: return
        ++document.modificationStamp
        CodeFoldingManager.getInstance(project).updateFoldRegions(editor)
    }

    fun get(): KotlinNotebookApplicationOptionsProvider {
        return options
    }
}
