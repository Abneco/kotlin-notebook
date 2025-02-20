// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.openapi.vfs.VirtualFile

sealed interface NotebookTreeItem {
    fun displayName(): String
    fun children(): List<NotebookTreeItem> = emptyList()
}

class NotebookItem(val file: VirtualFile) : NotebookTreeItem {
    override fun displayName(): String = file.name
    fun searchName(): String = file.name.lowercase()
}

class RootItem(notebooks: List<VirtualFile>) : NotebookTreeItem {
    private val notebookItems = notebooks.map { NotebookItem(it) }

    override fun displayName(): String = ""
    override fun children(): List<NotebookTreeItem> = notebookItems
}
