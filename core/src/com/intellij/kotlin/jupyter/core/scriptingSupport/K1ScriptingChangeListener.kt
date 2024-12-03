// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.util.getInjectedKtFiles
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import com.intellij.kotlin.jupyter.core.util.toPsiFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.configuration.listener.ScriptChangeListener

class K1ScriptingChangeListener(project: Project) : ScriptChangeListener(project) {
    override fun editorActivated(vFile: VirtualFile) {
        val notebookFile = getNotebookFileOrNull(vFile) ?: return
        val psiFile = notebookFile.file.toPsiFile(project) ?: return
        val injectedFiles = psiFile.getInjectedKtFiles()

        for (file in injectedFiles) {
            default.ensureUpToDatedConfigurationSuggested(
                file,
                skipNotification = true,
                forceSync = ApplicationManager.getApplication().isUnitTestMode
            )
        }
    }

    override fun documentChanged(vFile: VirtualFile) = Unit

    override fun isApplicable(vFile: VirtualFile): Boolean {
        return getNotebookFileOrNull(vFile) != null
    }

    private fun getNotebookFileOrNull(vFile: VirtualFile): BackedNotebookVirtualFile? {
        val originalFile = when (vFile) {
            is VirtualFileWindow -> vFile.delegate
            else -> vFile
        }

        return originalFile.toKotlinNotebookBackedFile()
    }
}