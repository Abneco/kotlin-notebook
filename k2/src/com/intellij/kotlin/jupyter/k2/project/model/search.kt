// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.project.model

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import org.jetbrains.kotlin.idea.KotlinScriptEntity

fun BackedNotebookVirtualFile.findK2WorkspaceEntityDependencies(project: Project): List<VirtualDirectoryImpl> {
    val currentSnapshot = project.workspaceModel.currentSnapshot
    val index = currentSnapshot.getVirtualFileUrlIndex()
    val entity = index.findEntitiesByUrl(file.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager()))
        .filterIsInstance<KotlinScriptEntity>().firstOrNull() ?: return emptyList()

    return entity.dependencies
        .mapNotNull { currentSnapshot.resolve(it) }
        .flatMap { it.classes }
        .mapNotNull { it.virtualFile as? VirtualDirectoryImpl }
}