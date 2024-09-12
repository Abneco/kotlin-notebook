// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.projectModel

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyLibraryEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager.Companion.toVfsRoots
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper

fun MutableEntityStorage.createOrUpdateLibraryDependency(
    moduleName: String,
    project: Project,
    entitySource: EntitySource,
    configurationWrapper: ScriptCompilationConfigurationWrapper
): LibraryDependency {

    val roots = getLibraryRoots(project, configurationWrapper)
    val libraryTableId = LibraryTableId.ModuleLibraryTableId(moduleId = ModuleId(moduleName))
    val entity = resolve(LibraryId("$moduleName dependencies", libraryTableId))
    if (entity != null) {
        modifyLibraryEntity(entity) {
            this.roots = roots.toMutableList()
            this.entitySource = entitySource
            this.tableId = libraryTableId
        }
        return LibraryDependency(entity.symbolicId, false, DependencyScope.COMPILE)
    }

    val dependencyEntity =
        addEntity(LibraryEntity("$moduleName dependencies", libraryTableId, roots, entitySource))

    return LibraryDependency(dependencyEntity.symbolicId, false, DependencyScope.COMPILE)
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