// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.project.model

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.workSpaceSnapshot
import com.intellij.kotlin.jupyter.k2.scriptingSupport.KotlinNotebookScriptEntitySource
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap

fun BackedNotebookVirtualFile.findK2WorkspaceModule(project: Project): Module? {
    val moduleName = file.toK2RuntimeModuleName(project)
    val workSpaceSnapshot = project.workSpaceSnapshot

    val entity = workSpaceSnapshot.entitiesBySource {
        it is KotlinNotebookScriptEntitySource
    }.firstOrNull {
        it is ModuleEntity && it.name == moduleName
    } as ModuleEntity?

    if (entity == null) {
        return null
    }

    return workSpaceSnapshot.moduleMap.getDataByEntity(entity) as? Module
}

/**
 * Returns a Sequence of all [WorkspaceEntity] related to a
 * given [VirtualFile]'s [com.intellij.platform.workspace.storage.url.VirtualFileUrl]
 */
internal fun VirtualFile.workspaceEntities(project: Project, snapshot: EntityStorage): Sequence<WorkspaceEntity> {
    val virtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val virtualFileUrl = toVirtualFileUrl(virtualFileUrlManager)
    return snapshot.getVirtualFileUrlIndex()
        .findEntitiesByUrl(virtualFileUrl)
}

internal fun Collection<LibraryEntity>.filterBoundToOneModule(snapshot: EntityStorage, targetModule: ModuleEntity): Collection<LibraryEntity> {
    val notebookModules = snapshot.entitiesBySource { it is KotlinNotebookScriptEntitySource }
        .filterIsInstance<ModuleEntity>() - targetModule

    return filter { library ->
        when {
            library.isForCompiledSnippets() -> true
            else -> {
                library.hasNoModuleReferences(notebookModules)
            }
        }
    }
}

private fun LibraryEntity.hasNoModuleReferences(modules: Sequence<ModuleEntity>): Boolean {
    val libraryId = this.symbolicId
    return modules.none { module ->
        libraryId in module.dependencies.mapNotNull { (it as? LibraryDependency)?.library }
    }
}
