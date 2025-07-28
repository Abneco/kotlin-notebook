// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
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
    ): Collection<KotlinScriptLibraryEntityId> {
        val (classes, sources) = getAllLibraryRoots(project)
        if (classes.isEmpty()) return emptyList()

        val libraryId = KotlinScriptLibraryEntityId(classes, sources)
        if (!entityStorage.contains(libraryId)) {
            entityStorage addEntity KotlinScriptLibraryEntity(classes, sources, KotlinNotebookScriptEntitySource)
        }

        return setOf(libraryId)
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
    ): Collection<KotlinScriptLibraryEntityId> {
        val (classes, sources) = getAllLibraryRoots(project)
        if (classes.isEmpty()) return emptyList()

        return buildSet {
            for (virtualFileUrl in classes) {
                val libraryName = virtualFileUrl.virtualFile?.name ?: virtualFileUrl.presentableUrl
                val presentableName = virtualFileUrl.virtualFile?.nameWithoutExtension ?: libraryName

                // This is a workaround to find a matching source's jar without adding all the source roots
                val sourceRoot = sources.firstOrNull {
                    it.presentableUrl.contains(presentableName)
                }

                val id = KotlinScriptLibraryEntityId(listOf(virtualFileUrl), listOfNotNull(sourceRoot))
                if (!entityStorage.contains(id)) {
                    entityStorage addEntity KotlinScriptLibraryEntity(id.classes, id.sources, KotlinNotebookScriptEntitySource)
                }

                add(id)
            }
        }
    }
}