// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.idea.core.script.ScriptClassPathUtil
import java.io.File

/**
 * Is used to separate script compilation configurations dependencies by 2 main read-only group:
 *  - [CompiledSnippets] group, which consists of all kernel compiled artifacts plus initial dependency
 *  - [Jars] group, which consists of all runtime dependencies being resolved and added by kernel.
 *
 * This separation is used to populate workspace module dependencies accordingly.
 */
sealed interface NotebookConfigurationRootsView {
    /**
     * Presentable name for deps type
     */
    val typeName: String

    /**
     * Provides a view on dependencies [OrderRootType.CLASSES] roots
     */
    val dependenciesRoots: List<File>

    /**
     * Provides a view on [OrderRootType.CLASSES] dependencies roots
     */
    val dependenciesSources: List<File>

    /**
     * Returns a list of all roots in the format as [LibraryRoot]
     */
    fun getAllLibraryRoots(project: Project): List<LibraryRoot> {
        val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

        val roots = buildSet {
            dependenciesRoots.mapNotNullTo(this) {
                val file = ScriptClassPathUtil.findVirtualFile(it.path)
                file?.let { LibraryRoot(file.toVirtualFileUrl(fileUrlManager), LibraryRootTypeId.COMPILED) }
            }

            dependenciesSources.mapNotNullTo(this) {
                val file = ScriptClassPathUtil.findVirtualFile(it.path)
                file?.let { LibraryRoot(file.toVirtualFileUrl(fileUrlManager), LibraryRootTypeId.SOURCES) }
            }
        }

        return roots.toList()
    }

    /**
     * Creates or updates an [entityStorage] based on the dependency roots.
     */
    fun getOrUpdateLibraryDependencies(project: Project, entityStorage: MutableEntityStorage): List<LibraryDependency>
}


internal fun KotlinNotebookScriptsModuleConfigurationInfo.createConfigurationDependencyViews(): List<NotebookConfigurationRootsView> {
    val info = this
    return buildList {
        add(CompiledSnippets(info))
        add(Jars(info))
    }
}
