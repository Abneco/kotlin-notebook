// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.kotlin.jupyter.core.util.sourceRootsForProjectModuleDependencies
import com.intellij.kotlin.jupyter.core.util.toBackedNotebookFile
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * Base class for manipulating with [NotebookConfigurationRootsView]
 */
abstract class NotebookConfigurationRootsViewBase(
    protected val project: Project,
    protected val configurationInfo: KotlinNotebookScriptModel
) : NotebookConfigurationRootsView {
    override val dependenciesRoots: List<Path> get() =
        filterTargetDependencies(project, configurationInfo.refinedConfiguration.dependenciesClassPath.map { it.toPath() })
    override val dependenciesSources: List<Path> get() =
        filterTargetDependencies(project, configurationInfo.refinedConfiguration.dependenciesSources.map { it.toPath() })

    protected abstract fun filterTargetDependencies(project: Project, candidates: List<Path>): List<Path>
}

/**
 * This group encapsulates only kernel-produced compilation artifacts, like snippets.
 * Project roots are not included here to prevent accidental conflicts with project sources.
 */
class CompiledSnippets(
    project: Project,
    configurationInfo: KotlinNotebookScriptModel
) : NotebookConfigurationRootsViewBase(project, configurationInfo) {
    override val typeName: String = "Compiled"

    override fun filterTargetDependencies(project: Project, candidates: List<Path>): List<Path> {
        val backedNotebookFile = configurationInfo.virtualFile.toBackedNotebookFile()
        val projectModuleDependencies = project
            .sourceRootsForProjectModuleDependencies(backedNotebookFile)
            .toSet()
        return candidates.filter { it.isDirectory() && it !in projectModuleDependencies }
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
class Jars(
    project: Project,
    configurationInfo: KotlinNotebookScriptModel
) : NotebookConfigurationRootsViewBase(project, configurationInfo) {
    override val typeName: String = "Jars"

    override fun filterTargetDependencies(project: Project, candidates: List<Path>): List<Path> {
        return candidates.filter { it.isRegularFile() && it.extension == "jar" }
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