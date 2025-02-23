// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls

sealed interface NotebookItem {
    fun displayName(): @Nls String
    fun searchName(): String
    fun children(): List<NotebookItem> = emptyList()
}

class NotebookFileItem(val file: VirtualFile) : NotebookItem {
    override fun displayName(): String = file.name
    override fun searchName(): String = file.name.lowercase()
}

class NotebookRootItem(notebooks: List<VirtualFile>) : NotebookItem {
    private val notebookItems = notebooks.map { NotebookFileItem(it) }

    override fun displayName(): String = ""
    override fun searchName(): String = ""
    override fun children(): List<NotebookItem> = notebookItems
}
