// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle
import javax.swing.JComponent

class KotlinNotebookConfigurable(private val project: Project) : SearchableConfigurable {
    private lateinit var panel: KotlinNotebookSettingsPanel
    private val projectOptions = KotlinNotebookProjectOptionsProvider.getInstance(project)

    override fun getId(): String {
        return ID
    }

    override fun getDisplayName(): String {
        return JupyterKotlinBundle.getMessage("kotlin.jupyter.settings.title")
    }

    override fun apply() {
        return panel.apply()
    }

    override fun isModified(): Boolean {
        return panel.isModified()
    }

    override fun createComponent(): JComponent {
        panel = KotlinNotebookSettingsPanel(
            project,
            projectOptions
        )
        return panel.createPanel()
    }

    companion object {
        const val ID = "kotlinNotebook"
    }
}