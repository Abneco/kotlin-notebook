// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.ui

import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel

/**
 * Entry point for setting up the Kotlin Notebook settings panel and handle its lifecycle.
 */
internal class KotlinNotebookConfigurable(private val project: Project) :
    BoundConfigurable(KotlinNotebookBundle.message("kotlin.jupyter.settings.title")), SearchableConfigurable {

    override fun getId(): String = ID

    override fun createPanel(): DialogPanel {
        val parentDisposable = disposable ?: error("Panel disposable must not be null in createPanel()")
        return KotlinNotebookSettingsPanelBuilder(project, parentDisposable).createPanel()
    }

    companion object {
        private const val ID = "kotlinNotebook"
    }
}
