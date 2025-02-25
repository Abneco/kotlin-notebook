// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.language

import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project

enum class NotebookTemplate(
    val id: String,
) {
    EMPTY("empty"),
    QUICK_API_TEST("quickApiTest"),
    DATA_ANALYSIS("dataAnalysis"),
}

val NotebookTemplate.displayName: String get() =
    KotlinNotebookBundle.message("kotlin.notebook.project.templates.$id.name")

val NotebookTemplate.description: String get() =
    KotlinNotebookBundle.message("kotlin.notebook.project.templates.$id.description")

val NotebookTemplate.templateName: String get() =
    "kotlin.jupyter.$id"

fun NotebookTemplate.getFileTemplate(project: Project): FileTemplate {
    return FileTemplateManager.getInstance(project)
        .getInternalTemplate(templateName)
}

val Project.emptyNotebookTemplate: FileTemplate
    get() = NotebookTemplate.EMPTY.getFileTemplate(project = this)

val FILE_TEMPLATE_KEY: DataKey<FileTemplate> = DataKey.create("kotlin.jupyter.scratch.template")
