// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings.ui

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle

internal class KotlinNotebookConfigurable(private val project: Project) :
    BoundConfigurable(KotlinNotebookBundle.message("kotlin.jupyter.settings.title")), SearchableConfigurable {

    override fun getId(): String = ID

    override fun createPanel(): DialogPanel {
        val parentDisposable = disposable ?: error("Panel disposable must not be null in createPanel()")
        return KotlinNotebookSettingsPanelBuilder(project, parentDisposable).createPanel()
    }

    companion object {
        const val ID = "kotlinNotebook"
    }
}