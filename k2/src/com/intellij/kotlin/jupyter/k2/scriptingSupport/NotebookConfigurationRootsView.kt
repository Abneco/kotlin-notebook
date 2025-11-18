// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.k2.configurations.toVirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
import java.nio.file.Path
import kotlin.io.path.pathString

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
    val dependenciesRoots: List<Path>

    /**
     * Provides a view on [OrderRootType.SOURCES] dependencies roots
     */
    val dependenciesSources: List<Path>

    /**
     * Returns a list of all roots in the format as [LibraryRoot]
     */
    fun getAllLibraryRoots(project: Project): Pair<List<VirtualFileUrl>, List<VirtualFileUrl>> {
        val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

        val classes = dependenciesRoots.map { it.pathString.toVirtualFileUrl(fileUrlManager) }
        val sources = dependenciesSources.map { it.pathString.toVirtualFileUrl(fileUrlManager) }

        return classes to sources
    }

    /**
     * Creates or updates an [entityStorage] based on the dependency roots.
     */
    fun getOrUpdateLibraryDependencies(project: Project, entityStorage: MutableEntityStorage): Collection<KotlinScriptLibraryEntityId>
}


internal fun KotlinNotebookScriptModel.createConfigurationDependencyViews(project: Project): List<NotebookConfigurationRootsView> {
    val info = this
    return buildList {
        add(CompiledSnippets(project, info))
        add(Jars(project, info))
    }
}
