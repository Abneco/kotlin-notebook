// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifacts.ARTIFACTS_COMMON_PREFIX
import com.intellij.kotlin.jupyter.k2.project.model.createOrUpdateLibraryForNotebookDependencies
import com.intellij.kotlin.jupyter.k2.project.model.toK2RuntimeDependencyLibraryName
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.idea.core.script.getOrCreateLibrary
import java.io.File

/**
 * Base class for manipulating with [NotebookConfigurationRootsView]
 */
abstract class NotebookConfigurationRootsViewBase(
    protected val configurationInfo: KotlinNotebookScriptsModuleConfigurationInfo
) : NotebookConfigurationRootsView {
    override val dependenciesRoots: List<File> get() =
        filterTargetDependencies(configurationInfo.configuration.dependenciesClassPath)
    override val dependenciesSources: List<File> get() =
        filterTargetDependencies(configurationInfo.configuration.dependenciesSources)

    protected abstract fun filterTargetDependencies(candidates: List<File>): List<File>

    protected fun getNotebookEntitySource(project: Project): KotlinNotebookScriptEntitySource {
        val urlManager = project.workspaceModel.getVirtualFileUrlManager()
        val notebookFileUrl = configurationInfo.notebookFile.toVirtualFileUrl(urlManager)
        return KotlinNotebookScriptEntitySource(notebookFileUrl)
    }
}

/**
 * This group encapsulates only kernel-produced compilation artifacts, like snippets.
 */
class CompiledSnippets(configurationInfo: KotlinNotebookScriptsModuleConfigurationInfo) : NotebookConfigurationRootsViewBase(configurationInfo) {
    override val typeName: String = "Compiled"

    override fun filterTargetDependencies(candidates: List<File>): List<File> {
        return candidates.filter { it.isDirectory }
    }

    override fun getOrUpdateLibraryDependencies(
        project: Project,
        entityStorage: MutableEntityStorage
    ): List<LibraryDependency> {
        val notebook = configurationInfo.notebookFile
        val libraryName = notebook.toK2RuntimeDependencyLibraryName(project, typeName)
        val entitySource = getNotebookEntitySource(project)

        val libraryEntity = entityStorage.createOrUpdateLibraryForNotebookDependencies(
            libraryName,  entitySource, getAllLibraryRoots(project)
        )

        return listOf(
            LibraryDependency(libraryEntity.symbolicId, false, DependencyScope.COMPILE)
        )
    }
}

/**
 * This group encapsulates all resolved jar dependencies.
 */
class Jars(configurationInfo: KotlinNotebookScriptsModuleConfigurationInfo) : NotebookConfigurationRootsViewBase(configurationInfo) {
    override val typeName: String = "Jars"

    override fun filterTargetDependencies(candidates: List<File>): List<File> {
        return candidates.filter { it.isFile && it.extension == "jar" }
    }

    override fun getOrUpdateLibraryDependencies(
        project: Project,
        entityStorage: MutableEntityStorage
    ): List<LibraryDependency> {
        val jarRoots = getAllLibraryRoots(project)
        val classRoots = jarRoots.filter { root -> root.type == LibraryRootTypeId.COMPILED }
        val entitySource = getNotebookEntitySource(project)

        return buildList {
            for (root in classRoots) {
                val virtualFile = root.url.virtualFile
                val libraryName = virtualFile?.name ?: root.url.presentableUrl
                val presentableName = virtualFile?.nameWithoutExtension ?: libraryName

                // This is a workaround to find a matching source's jar without adding all the source roots
                val sourceRoot = jarRoots.firstOrNull {
                    root -> root.type == LibraryRootTypeId.SOURCES && root.url.presentableUrl.contains(presentableName)
                }

                with(entityStorage) {
                    add(
                        getOrCreateLibrary(libraryName, listOfNotNull(root, sourceRoot), entitySource)
                    )
                }
            }
        }
    }
}