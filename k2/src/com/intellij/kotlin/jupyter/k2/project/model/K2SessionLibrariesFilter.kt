// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.project.model

import com.intellij.kotlin.jupyter.core.projectModel.KotlinNotebookPermanentIndexService
import com.intellij.kotlin.jupyter.core.projectModel.KotlinNotebookSessionLibrariesFilter
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.utils.mapToSetOrEmpty

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
        val permanentClassRoots = KotlinNotebookPermanentIndexService.getInstance(project).currentClassRoots.mapToSetOrEmpty {
            it.presentableName
        }

        return libraries.filter { library ->
            library.isProjectRelated(permanentClassRoots)
        }
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
