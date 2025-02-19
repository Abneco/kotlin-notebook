// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

@Service
class RecentKotlinNotebooksService {
    fun getNotebooks(): List<VirtualFile> {
        val scratchService = ScratchFileService.getInstance()
        val rootType = ScratchRootType.getInstance()
        val rootPath = scratchService.getRootPath(rootType)
        val rootDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(rootPath) ?: return emptyList()

        return collectNotebooks(rootDir, scratchService, rootType)
            .sortedByDescending { it.timeStamp }
    }

    private fun collectNotebooks(dir: VirtualFile, scratchService: ScratchFileService, rootType: ScratchRootType): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        for (child in dir.children) {
            if (child.isDirectory) {
                result.addAll(collectNotebooks(child, scratchService, rootType))
            } else if (child.fileType is JupyterFileType && scratchService.getRootType(child) == rootType) {
                result.add(child)
            }
        }
        return result
    }

    companion object {
        fun getInstance(): RecentKotlinNotebooksService = service<RecentKotlinNotebooksService>()
    }
}
