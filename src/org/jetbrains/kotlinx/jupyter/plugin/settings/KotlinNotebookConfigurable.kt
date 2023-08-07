// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle

class KotlinNotebookConfigurable(private val project: Project) :
    BoundConfigurable(KotlinNotebookBundle.getMessage("kotlin.jupyter.settings.title")), SearchableConfigurable {

    override fun getId(): String = ID

    override fun createPanel(): DialogPanel {
        val optionsProvider = KotlinNotebookProjectOptionsProvider.getInstance(project)
        return KotlinNotebookSettingsPanel.createPanel(project, optionsProvider, service(), disposable!!)
    }

    companion object {
        const val ID = "kotlinNotebook"
    }
}