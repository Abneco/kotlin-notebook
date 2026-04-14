// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.kotlin.jupyter.core.settings.recents.RecentNotebookWithIcon
import org.jetbrains.annotations.Nls

sealed interface NotebookItem {
    @Nls fun displayName(): String
    fun searchName(): String
    fun children(): List<NotebookItem> = emptyList()
}

class NotebookFileItem(val notebookWithIcon: RecentNotebookWithIcon) : NotebookItem {
    private val notebookPath get() = notebookWithIcon.notebook.path

    override fun displayName(): String = notebookPath.name
    override fun searchName(): String = notebookPath.name.lowercase()
}

class NotebookRootItem(notebooksWithIcons: List<RecentNotebookWithIcon>) : NotebookItem {
    private val notebookItems = notebooksWithIcons.map { NotebookFileItem(it) }

    override fun displayName(): String = ""
    override fun searchName(): String = ""
    override fun children(): List<NotebookItem> = notebookItems
}
