// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.language

import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.kotlin.jupyter.core.jupyter.actions.CreateNotebookFactory
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project

val Project.emptyNotebookTemplate: FileTemplate
    get() = FileTemplateManager.getInstance(this)
        .getInternalTemplate(CreateNotebookFactory.TEMPLATE_NAME)

val FILE_TEMPLATE_KEY: DataKey<FileTemplate> = DataKey.create("kotlin.jupyter.scratch.template")
