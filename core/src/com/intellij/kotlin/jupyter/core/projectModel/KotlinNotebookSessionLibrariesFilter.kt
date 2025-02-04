// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectModel

import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.roots.libraries.Library

/**
 * Interface is used to determine whether a given project Library can be used as a dependency for the Jupyter Session.
 * It's worth noting that in K2 mode, for each [com.intellij.openapi.module.Module], a set of [Library]s is created.
 * In each of such a Library, particular compiled snippets are stored, among other things, making it effectively internal.
 *
 * @see com.intellij.kotlin.jupyter.core.projectModel.JupyterKotlinProjectArtifactsService.getLibraries
 */
fun interface KotlinNotebookSessionLibrariesFilter : KotlinPluginModeAwareHandler {
    /**
     * Should return true if a library should not be exposed, e.g.,
     * it contains REPL backend artifacts or anything not suitable for sharing.
     */
    fun isInternalLibrary(library: Library): Boolean

    companion object {
        private val EP: ExtensionPointName<KotlinNotebookSessionLibrariesFilter> = ExtensionPointName.create("com.intellij.kotlin.jupyter.core.sessionLibrariesFilter")

        fun filterSessionLibraries(libraries: List<Library>): List<Library> {
            val extensions = EP.extensionList
            return libraries.filterNot { library ->
                extensions.any { extension ->
                    extension.isInternalLibrary(library)
                }
            }
        }
    }
}