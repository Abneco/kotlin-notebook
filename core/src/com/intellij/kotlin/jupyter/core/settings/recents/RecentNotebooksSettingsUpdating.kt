// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.recents

import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

fun KotlinNotebookApplicationOptions.addRecentNotebook(
    notebook: RecentNotebook
) {
    val newItem = RecentNotebookState().apply {
        path = notebook.path.path
        projectPath = notebook.projectPath.path
        timeStamp = notebook.timeStamp.toString()
    }
    val recentNotebooks = get().recentNotebooks
    if (newItem in recentNotebooks) return

    recentNotebooks.add(newItem)
}

fun KotlinNotebookApplicationOptions.getRecentNotebooks(): List<RecentNotebook> {
    return get().recentNotebooks.mapNotNull { recentNotebook ->
        val notebookPath = getVirtualFileByPath(recentNotebook.path) ?: return@mapNotNull null
        val projectPath = getVirtualFileByPath(recentNotebook.projectPath) ?: return@mapNotNull null
        val timestamp = recentNotebook.timeStamp?.toLongOrNull() ?: return@mapNotNull null

        RecentNotebook(
            notebookPath,
            projectPath,
            timestamp,
        )
    }
}

val Project.rootPath: VirtualFile? get() {
    return getBaseDirectories().firstOrNull()
}

private fun getVirtualFileByPath(path: String?): VirtualFile? {
    if (path == null) return null
    val file = Path.of(path)
    return VfsUtil.findFile(file, true)
}
