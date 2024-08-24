// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.language

import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.util.application
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions.CreateNotebookFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions.NotebookMode
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

/**
 * Helper for creating Kotlin Notebook scratch files as they need a specific JSON format
 * to be considered valid files.
 */
class JupyterKotlinScratchCreationHelper: ScratchFileCreationHelper() {

    override fun prepareText(project: Project, context: Context, dataContext: DataContext): Boolean {
        // To create a Kotlin Notebook file, we need to use a file template.
        // Unfortunately, this creates a conflict as both `ScratchFileActions` and
        // `CreateNotebookFactory` want to take control of the file creation process.
        // To work around this, we instead write the initial template to a temporary file,
        // read the content from there and then pass it to the `ScratchCreationHelper.Context`.
        try {
            application.runWriteAction {
                val tempDir = FileUtil.generateRandomTemporaryPath("kotlin-notebook", "")
                val fileName = "template.ipynb"
                val dir = VfsUtil.createDirectories(tempDir.path)
                val psiDir: PsiDirectory = PsiManager.getInstance(project).findDirectory(dir)!!
                val templateFile = CreateNotebookFactory.createFile(fileName, psiDir, false, NotebookMode.LIGHT)!!
                val backedFile = BackedNotebookVirtualFile.findOrCreate(templateFile.virtualFile)
                val notebookJsonText = backedFile.notebook.json.toPrettyString()
                context.text = notebookJsonText
                context.language = null
                context.fileExtension = JupyterFileType.defaultExtension
                context.createOption = ScratchFileService.Option.create_new_always
                psiDir.delete()
            }
        } catch (ex: Exception) {
            val message = KotlinNotebookBundle.message("action.CreateNewScratchKotlinNotebook.error.dialog.text", ex.message ?: "")
            throw IllegalStateException(message, ex)
        }
        return true
    }
}