// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectModel.extensions

import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library

/**
 * Interface is used to determine whether a given project Library can be used as a dependency for the Jupyter Session.
 * It's worth noting that in K2 mode, for each [com.intellij.openapi.module.Module], a set of [com.intellij.openapi.roots.libraries.Library]s is created.
 * In each of such a Library, particular compiled snippets are stored, among other things, making it effectively internal.
 *
 * @see com.intellij.kotlin.jupyter.core.projectModel.JupyterKotlinProjectArtifactsService.getLibraries
 */
fun interface KotlinNotebookSessionLibrariesFilter : KotlinPluginModeAwareHandler {
    /**
     * Returns a list of libraries which are not notebook-created runtime libraries, e.g.,
     * they do not contain REPL backend artifacts or anything not project-related.
     */
    fun filterOutNonProjectLibraries(project: Project, libraries: List<Library>): List<Library>

    companion object {
        private val EP: ExtensionPointName<KotlinNotebookSessionLibrariesFilter> = ExtensionPointName.create("com.intellij.kotlin.jupyter.core.sessionLibrariesFilter")

        fun filterSessionLibraries(project: Project, libraries: List<Library>): List<Library> {
            val extensions = EP.extensionList
            if (extensions.isEmpty()) return libraries

            return extensions.fold(libraries) { acc, filter ->
                filter.filterOutNonProjectLibraries(project, acc)
            }.toList()
        }
    }
}