// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.performancePlugin.commands

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.kotlin.jupyter.core.jupyter.actions.CreateNotebookFactory
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.psi.PsiManager


/**
 * Command creates a kotlin notebook.
 * Example: %newKotlinNotebook filename.ipynb
 */
class NewKotlinNotebookCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
    companion object {
        const val PREFIX: String = AbstractCommand.CMD_PREFIX + "newKotlinNotebook"
    }

    override suspend fun doExecute(context: PlaybackContext) {
        extractCommandArgument(PREFIX).split(' ').filter { it.isNotEmpty() }.also {
            if (it.size != 1) {
                context.error("Unexpected number of arguments. Should be 1 args", line)
                return
            }
            val fileName = it.first()
            deleteFileIfExist(context, fileName)
            val project = context.project
            val projectDir = project.guessProjectDir() ?: return
            val psiDirectory = readAction { PsiManager.getInstance(project).findDirectory(projectDir) } ?: return
            val template = FileTemplateManager.getInstance(project)
                .getInternalTemplate("kotlin.jupyter.empty")

            edtWriteAction {
                CreateNotebookFactory.createFileFromTemplate(
                    fileName,
                    template,
                    psiDirectory
                )
            }
        }
    }

    private suspend fun deleteFileIfExist(context: PlaybackContext, fileName: String) {
        val project = context.project
        val projectDir = project.guessProjectDir() ?: return
        val file = readAction { projectDir.findChild(fileName) } ?: return

        edtWriteAction {
            if (file.exists()) {
                file.delete(this@NewKotlinNotebookCommand)
            }
        }
    }
}