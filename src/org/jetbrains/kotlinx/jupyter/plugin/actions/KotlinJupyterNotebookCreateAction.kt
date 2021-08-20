// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import icons.KotlinJupyterIcons
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec
import org.jetbrains.kotlinx.jupyter.config.notebookLanguageInfo
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle
import org.jetbrains.plugins.notebooks.jupyter.actions.createFileFromTemplateWithProperties

class KotlinJupyterNotebookCreateAction : CreateFileFromTemplateAction(
    JupyterKotlinBundle.messagePointer("kotlin.jupyter.action.create.notebook.text"),
    JupyterKotlinBundle.messagePointer("kotlin.jupyter.action.create.notebook.description"),
    KotlinJupyterIcons.FileIcon
), DumbAware {

    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder
            .setTitle(JupyterKotlinBundle.message("kotlin.jupyter.action.create.notebook.dialog.title"))
            .addKind(JupyterKotlinBundle.message("kotlin.jupyter.action.create.notebook.dialog.kind"), KotlinJupyterIcons.FileIcon, "Kotlin Jupyter Notebook")
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String) =
        JupyterKotlinBundle.message("kotlin.jupyter.action.create.notebook.name", templateName)

    public override fun createFileFromTemplate(name: String, template: FileTemplate, dir: PsiDirectory): PsiFile? {
        val templateValues = createTemplateValues()
        return createFileFromTemplateWithProperties(name, template, dir, defaultTemplateProperty, templateValues, LOG)
    }

    companion object {
        private val LOG = logger<KotlinJupyterNotebookCreateAction>()

        private const val VAR_KERNEL_SPEC = "KERNEL_SPEC"
        private const val VAR_LANGUAGE_SPEC = "LANGUAGE_SPEC"

        internal fun createTemplateValues(): Map<String, String> {
            val serializer = Json {
                prettyPrint = true
            }
            val kernelSpec = serializer.encodeToString(notebookKernelSpec)
            val languageSpec = serializer.encodeToString(notebookLanguageInfo)

            return mapOf(
                VAR_KERNEL_SPEC to kernelSpec,
                VAR_LANGUAGE_SPEC to languageSpec,
            )
        }
    }
}
