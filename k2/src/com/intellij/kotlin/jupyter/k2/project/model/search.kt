// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.project.model

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity

/**
 * Finds all [KotlinScriptEntity] in the workspace model associated with the given [BackedNotebookVirtualFile].
 */
fun BackedNotebookVirtualFile.findK2WorkspaceScriptEntities(workspaceModel: WorkspaceModel): Sequence<KotlinScriptEntity> {
    val currentSnapshot = workspaceModel.currentSnapshot
    val index = currentSnapshot.getVirtualFileUrlIndex()
    val notebookFileUrl = file.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())

    return index.findEntitiesByUrl(notebookFileUrl)
        .filterIsInstance<KotlinScriptEntity>()
}

/**
 * Finds all dependencies of [KotlinScriptEntity] associated with the given [BackedNotebookVirtualFile].
 * It's expected that the notebook file is associated with a single script entity.
 */
fun BackedNotebookVirtualFile.findK2WorkspaceEntityDependencies(project: Project): List<VirtualDirectoryImpl> {
    val model = project.workspaceModel
    val currentSnapshot = model.currentSnapshot
    val entity = findK2WorkspaceScriptEntities(model).firstOrNull() ?: return emptyList()

    return entity.dependencies
        .mapNotNull { currentSnapshot.resolve(it) }
        .flatMap { it.classes }
        .mapNotNull { it.virtualFile as? VirtualDirectoryImpl }
}