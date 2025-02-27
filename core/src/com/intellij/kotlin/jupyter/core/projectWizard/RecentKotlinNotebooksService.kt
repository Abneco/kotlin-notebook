// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile

@Service
class RecentKotlinNotebooksService {
    fun getNotebooks(): List<VirtualFile> {
        val rootDir = DefaultKotlinNotebookProject.getVirtualFileRoot()

        return collectNotebooks(rootDir)
            .sortedByDescending { it.timeStamp }
    }

    private fun collectNotebooks(dir: VirtualFile): List<VirtualFile> {
        return buildList {
            for (child in dir.children) {
                if (child.isDirectory) {
                    addAll(collectNotebooks(child))
                } else if (child.fileType is JupyterFileType) {
                    add(child)
                }
            }
        }
    }

    companion object {
        fun getInstance(): RecentKotlinNotebooksService = service<RecentKotlinNotebooksService>()
    }
}
