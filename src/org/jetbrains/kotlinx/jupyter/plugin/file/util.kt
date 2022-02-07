// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.file

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.NOTEBOOK_LANGUAGE
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterNotebookBase

val VirtualFile?.isKotlinNotebook: Boolean get() {
    return this?.notebookLanguage === KotlinLanguage.INSTANCE
}

private val VirtualFile.notebookLanguage: Language? get(){
    if (this is NotebookVirtualFile) {
        return notebook.language
    }

    return if (this is LightVirtualFile) {
        // It's copy of either notebook or origin file being modified
        val notebookFile = originalFile as? NotebookVirtualFile
            ?: (originalFile as? LightVirtualFile)?.originalFile as? NotebookVirtualFile
        notebookFile?.notebook?.language
            ?: originalFile?.getUserData(NOTEBOOK_LANGUAGE)
            ?: getLanguageFromOriginalFile(this)
    }
    else {
        // It's origin file
        getUserData(NOTEBOOK_LANGUAGE)
            ?: getLanguageFromOriginalFile(this)?.let { language ->
                language.also { putUserData(NOTEBOOK_LANGUAGE, it) }
            }
    }
}

private fun getLanguageFromOriginalFile(file: VirtualFile): Language? {
    return try {
        JupyterNotebookBase(file.inputStream.reader()).language
    } catch (e: Exception) {
        null
    }
}
