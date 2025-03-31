// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard.common

import com.intellij.kotlin.jupyter.core.projectWizard.DefaultKotlinNotebookProject
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.settings.recents.RecentNotebook
import com.intellij.kotlin.jupyter.core.settings.recents.addRecentNotebook
import com.intellij.kotlin.jupyter.core.statistics.fus.KotlinNotebookFeatureUsagesCollector
import com.intellij.kotlin.jupyter.core.statistics.fus.WelcomeScreenIdeEntryType
import com.intellij.openapi.fileEditor.FileEditorManager

fun openNotebook(notebook: RecentNotebook) {
    KotlinNotebookFeatureUsagesCollector.registerIdeEntryFromKotlinNotebookWelcomeScreen(
        WelcomeScreenIdeEntryType.OPEN_RECENT_NOTEBOOK
    )
    val projectPath = notebook.projectPath.toNioPath()
    val project = DefaultKotlinNotebookProject.getProjectWithModalProgress(projectPath)
    KotlinNotebookApplicationOptions.addRecentNotebook(notebook)
    FileEditorManager.getInstance(project).openFile(notebook.path, true)
}
