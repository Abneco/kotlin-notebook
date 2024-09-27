// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.k2

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.injectedScriptLibraryDependencies
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook

/**
 * Extension to provide dependencies specific to a Kotlin Notebook injected script.
 * Since one notebook may contain multiple scripts, structure of such dependency is hierarchical and
 * is returned by [getRelatedLibraries], so that call site is not dependent on the implementation.
 *
 * Note that it works for K2 mode; K1 is handled by a legacy approach.
 */
class KotlinNotebookScriptLibrariesProvider : ScriptAdditionalIdeaDependenciesProvider() {
    override fun getRelatedModules(file: VirtualFile, project: Project): List<Module> = emptyList()

    override fun getRelatedLibraries(file: VirtualFile, project: Project): List<Library> {
        val virtualFile = (file as? VirtualFileWindow)?.delegate ?: return emptyList()
        if (!virtualFile.isKotlinNotebook) return emptyList()

        val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
        val libraryDependencies = file.injectedScriptLibraryDependencies(project, snapshot)

        return libraryDependencies.mapNotNull { dependency ->
            val entity = snapshot.resolve(dependency.library) ?: return@mapNotNull null
            snapshot.libraryMap.getDataByEntity(entity)
        }
    }
}