// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.diagnostic.Logger
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
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.asJson
import org.jetbrains.plugins.notebooks.jupyter.actions.createFileFromTemplateWithProperties

class KotlinNotebookCreateAction : CreateFileFromTemplateAction(
    KotlinNotebookBundle.messagePointer("kotlin.jupyter.action.create.notebook.text"),
    KotlinNotebookBundle.messagePointer("kotlin.jupyter.action.create.notebook.description"),
    KotlinJupyterIcons.FileIcon
), DumbAware {

    @Suppress("DialogTitleCapitalization")
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder
            .setTitle(KotlinNotebookBundle.message("kotlin.jupyter.action.create.notebook.dialog.title"))
            .addKind(
                KotlinNotebookBundle.message("kotlin.jupyter.action.create.notebook.dialog.kind"),
                KotlinJupyterIcons.FileIcon,
                KOTLIN_JUPYTER_NOTEBOOK
            )
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String) =
        KotlinNotebookBundle.message("kotlin.jupyter.action.create.notebook.name", templateName)

    public override fun createFileFromTemplate(name: String, template: FileTemplate, dir: PsiDirectory): PsiFile? {
        val templateValues = createTemplateValues(dir.project)
        return createFileFromTemplateWithProperties(name, template, dir, defaultTemplateProperty, templateValues, LOG)
    }

    companion object {
        private val LOG = logger<KotlinNotebookCreateAction>()

        private const val KOTLIN_JUPYTER_NOTEBOOK = "Kotlin Jupyter Notebook"

        private const val VAR_KERNEL_SPEC = "KERNEL_SPEC"
        private const val VAR_LANGUAGE_SPEC = "LANGUAGE_SPEC"
        private const val VAR_KTNB_METADATA = "KTNB_METADATA"

        internal fun createTemplateValues(project: Project): Map<String, String> {
            val serializer = Json {
                prettyPrint = true
            }
            val kernelSpec = serializer.encodeToString(notebookKernelSpec)
            val languageSpec = serializer.encodeToString(notebookLanguageInfo)
            val notebookSettings = KotlinNotebookProjectOptionsProvider.getInstance(project).getNewKotlinNotebookSettings().asJson()

            return buildMap {
                put(VAR_KERNEL_SPEC, kernelSpec)
                put(VAR_LANGUAGE_SPEC, languageSpec)
                if (notebookSettings != null) {
                    put(VAR_KTNB_METADATA, notebookSettings)
                }
            }
        }

        fun createNotebook(name: String, directory: PsiDirectory, logger: Logger = LOG): PsiFile? {
            val notebookTemplateValues = createTemplateValues(directory.project)
            val notebookTemplate = FileTemplateManager.getInstance(directory.project).getInternalTemplate(KOTLIN_JUPYTER_NOTEBOOK)
            return createFileFromTemplateWithProperties(name, notebookTemplate, directory, null, notebookTemplateValues, logger)
        }
    }
}
