// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.project.model

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.k2.scriptingSupport.NotebookConfigurationRootsView
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
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.jetbrains.kotlin.idea.core.script.ScriptClassPathUtil


/**
 * Creates or updates a library entity associated with runtime compiled only notebook dependencies in a
 * global Project table.
 *
 * If a library entity for the specified notebook name already exists, it updates the entity's
 * roots and entity source.
 * If it does not exist, it creates a new entity.
 *
 * @param libraryName The name of the Notebook for which the library is being created or updated.
 * @param project The project context in which the library is being managed.
 * @param notebookEntitySource The entity source associated with the notebook.
 * @param roots List of library roots which will be used for this entity.
 */
fun MutableEntityStorage.createOrUpdateLibraryForNotebookDependencies(
    libraryName: String,
    notebookEntitySource: EntitySource,
    roots: List<LibraryRoot>
): LibraryEntity {
    val libraryTableId = LibraryTableId.ProjectLibraryTableId
    val entity = resolveLibraryDependencies(libraryName, libraryTableId)

    return if (entity != null) {
        modifyLibraryEntity(entity) {
            this.roots = roots.toMutableList()
            this.tableId = libraryTableId
        }
    } else {
        addEntity(
            LibraryEntity(
                libraryName, libraryTableId, roots, notebookEntitySource
            )
        )
    }
}

fun MutableEntityStorage.resolveLibraryDependencies(
    notebookDependentLibName: String,
    libraryTableId: LibraryTableId
): LibraryEntity? {
    return resolve(LibraryId(notebookDependentLibName, libraryTableId))
}

/**
 * Returns a list of [LibraryDependency] for a particular [VirtualFile] based
 * on [ModuleEntity] associated with this file.
 */
fun BackedNotebookVirtualFile.notebookScriptLibraryDependencies(project: Project, workSpaceSnapshot: EntityStorage): List<LibraryDependency> {
    val dependencies = findK2WorkspaceModule(project)
        ?.findModuleEntity(workSpaceSnapshot)
        ?.dependencies ?: return emptyList()

    return dependencies.filterIsInstance<LibraryDependency>()
}

fun BackedNotebookVirtualFile.notebookScriptLibrariesEntities(project: Project, workSpaceSnapshot: EntityStorage): List<LibraryEntity> {
    return notebookScriptLibraryDependencies(project, workSpaceSnapshot).mapNotNull {
        workSpaceSnapshot.resolve(it.library)
    }
}
