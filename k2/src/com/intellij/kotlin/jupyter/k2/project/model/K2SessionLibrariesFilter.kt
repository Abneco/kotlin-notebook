// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.project.model

import com.intellij.kotlin.jupyter.core.projectModel.extensions.KotlinNotebookSessionLibrariesFilter
import com.intellij.kotlin.jupyter.k2.scriptingSupport.KotlinNotebookScriptEntitySource
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.platform.backend.workspace.WorkspaceModel
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity

class K2SessionLibrariesFilter : KotlinNotebookSessionLibrariesFilter {
    override fun filterOutNonProjectLibraries(
        project: Project,
        libraries: List<Library>
    ): List<Library> {
        if (libraries.isEmpty()) return emptyList()

        return filterOutNotebookLibraries(project, libraries)
    }

    private fun filterOutNotebookLibraries(
        project: Project,
        libraries: List<Library>
    ): List<Library> {
        if (libraries.isEmpty()) return emptyList()
        val notebookClassRoots = collectNotebookLibraryRoots(project)

        return libraries.filter { library ->
            library.isProjectRelated(notebookClassRoots)
        }
    }

    private fun collectNotebookLibraryRoots(project: Project): Set<String> {
        val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
        return snapshot.entities(KotlinScriptLibraryEntity::class.java)
            .filter { it.entitySource is KotlinNotebookScriptEntitySource }
            .flatMap { it.classes }
            .mapTo(mutableSetOf()) { it.presentableUrl }
    }

    private fun Library.isProjectRelated(notebookDependenciesRoots: Collection<String>): Boolean {
        val libraryRoots = getFiles(OrderRootType.CLASSES).map {
            it.presentableName
        }

        return libraryRoots.none {
            it in notebookDependenciesRoots
        }
    }
}
