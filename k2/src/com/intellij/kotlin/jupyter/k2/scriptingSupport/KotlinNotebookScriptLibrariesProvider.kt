// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrNull
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.toBackedNotebookFile
import com.intellij.kotlin.jupyter.k2.project.model.notebookScriptLibraryDependencies
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import kotlinx.coroutines.async
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider

/**
 * Extension to provide dependencies specific to a Kotlin Notebook injected script.
 * Since one notebook may contain multiple scripts, structure of such dependency is hierarchical and
 * is returned by [getRelatedLibraries], so that call site is not dependent on the implementation.
 *
 * Note that it works for K2 mode; K1 is handled by a legacy approach.
 */
class KotlinNotebookScriptLibrariesProvider : ScriptAdditionalIdeaDependenciesProvider {
    companion object {
        private val LOG = notebookLogger()
    }
    override fun getRelatedLibraries(file: VirtualFile, project: Project): List<Library> {
        val virtualFile = file.getTopLevelFileOrNull()
        if (virtualFile == null) {
            LOG.debug("Top-level file not found: $file")
            return emptyList()
        }
        if (!virtualFile.isKotlinNotebook) return emptyList()

        val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
        val libraryDependencies = virtualFile.toBackedNotebookFile().notebookScriptLibraryDependencies(project, snapshot)
        if (libraryDependencies.isEmpty()) {
            LOG.warn("No library dependencies found for notebook file: ${virtualFile.name}")
            return emptyList()
        }

        val resolvedLibraries = libraryDependencies.mapNotNull { dependency ->
            snapshot.resolve(dependency.library) ?: return@mapNotNull null
        }
        KotlinNotebookPluginScope.getForProject(project).async {
            resolvedLibraries.logLibrariesRoots(virtualFile)
        }

        return resolvedLibraries.mapNotNull { dependency ->
            snapshot.libraryMap.getDataByEntity(dependency)
        }
    }

    private fun List<LibraryEntity>.logLibrariesRoots(virtualFile: VirtualFile) {
        val dependencies = this
        val rootsInfo = buildString {
            dependencies.forEach { libraryDependency ->
                append(
                    libraryDependency.roots.filter { it.type == LibraryRootTypeId.COMPILED }.joinToString("\n") { it.url.presentableUrl }
                )
            }
        }
        LOG.debug("Found ${dependencies.size} library dependencies for notebook file: ${virtualFile.name}, roots:\n$rootsInfo\n")
    }
}