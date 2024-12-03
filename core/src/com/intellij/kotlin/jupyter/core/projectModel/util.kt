// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectModel

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyLibraryEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull


/**
 * Creates or updates a library entity associated with runtime notebook dependencies in a
 * global Project table.
 *
 * If a library entity for the specified notebook name already exists, it updates the entity's
 * roots and entity source.
 * If it does not exist, it creates a new entity.
 *
 * @param notebookName The name of the Notebook for which the library is being created or updated.
 * @param project The project context in which the library is being managed.
 * @param notebookEntitySource The entity source associated with the notebook.
 * @param configurationWrapper Wrapper for script compilation configuration for any cell in the notebook, used to determine library roots.
 */
fun MutableEntityStorage.createOrUpdateLibraryForNotebookDependencies(
    notebookName: String,
    project: Project,
    notebookEntitySource: EntitySource,
    configurationWrapper: ScriptCompilationConfigurationWrapper
): LibraryEntity {
    val roots = getLibraryRoots(project, configurationWrapper)
    val libraryTableId = LibraryTableId.ProjectLibraryTableId
    val name = "$notebookName dependencies"
    val entity = resolveLibraryDependencies(notebookName, libraryTableId)

    return if (entity != null) {
        modifyLibraryEntity(entity) {
            this.roots = roots.toMutableList()
            this.tableId = libraryTableId
        }
    } else {
        addEntity(
            LibraryEntity(
                name, libraryTableId, roots, notebookEntitySource
            )
        )
    }
}

fun MutableEntityStorage.resolveLibraryDependencies(
    notebookName: String,
    libraryTableId: LibraryTableId
): LibraryEntity? {
    return resolve(LibraryId("$notebookName dependencies", libraryTableId))
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


fun VirtualFile.injectedScriptLibraryDependencies(project: Project, workSpaceSnapshot: EntityStorage): List<LibraryDependency> {
    val dependencies = workspaceEntities(project, workSpaceSnapshot)
        .firstIsInstanceOrNull<ModuleEntity>()?.dependencies ?: return emptyList()

    return dependencies.filterIsInstance<LibraryDependency>()
}

private fun getLibraryRoots(
    project: Project,
    configurationWrapper: ScriptCompilationConfigurationWrapper
): List<LibraryRoot> {
    val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

    val roots = buildList {
        toVfsRoots(configurationWrapper.dependenciesClassPath).mapTo(this) {
            LibraryRoot(it.toVirtualFileUrl(fileUrlManager), LibraryRootTypeId.COMPILED)
        }
    }

    return roots
}