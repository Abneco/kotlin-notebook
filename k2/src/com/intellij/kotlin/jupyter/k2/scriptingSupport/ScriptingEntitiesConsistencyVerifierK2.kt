// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.projectModel.kotlin.getIndexedTopLevelClassifiersFiltered
import com.intellij.kotlin.jupyter.core.scriptingSupport.ScriptingEntitiesConsistencyVerifier
import com.intellij.kotlin.jupyter.core.scriptingSupport.workSpaceSnapshot
import com.intellij.kotlin.jupyter.k2.project.model.findK2WorkspaceModule
import com.intellij.kotlin.jupyter.k2.project.model.notebookScriptLibrariesEntities
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.implicitReceivers

private class ScriptingEntitiesConsistencyVerifierFactoryK2 : ScriptingEntitiesConsistencyVerifier.Factory {
    override fun create(project: Project): ScriptingEntitiesConsistencyVerifier {
        return ScriptingEntitiesConsistencyVerifierK2(project)
    }
}

private class ScriptingEntitiesConsistencyVerifierK2(
    private val project: Project
): ScriptingEntitiesConsistencyVerifier {
    private fun getDependencyRootsForNotebook(notebookFile: BackedNotebookVirtualFile): List<LibraryRoot> {
        val snapshot = project.workSpaceSnapshot
        val dependencyAsLibraries = notebookFile
            .notebookScriptLibrariesEntities(project, snapshot)

        return dependencyAsLibraries.flatMap { it.roots }
    }

    private fun checkSourceIsNotEmpty(notebookFile: BackedNotebookVirtualFile): Boolean {
        val scriptConfigurationsSource = project.service<NotebookScriptConfigurationsManager>().cache
        return scriptConfigurationsSource.getConfigurationForNotebook(notebookFile.file) != null
    }

    /**
     * Only COMPILED roots influence analysis, so we're checking that the necessary artifacts are already present in the model
     * before analysis could start.
     */
    private fun checkArtifactPresentInLibrary(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: VirtualFileUrl): Boolean {
        val artifactName = lastCompiledScriptPath.fileName

        val libraryRoots = getDependencyRootsForNotebook(virtualFile)
        val isPresent = libraryRoots.any { root ->
            root.type == LibraryRootTypeId.COMPILED && root.url.presentableUrl.endsWith(artifactName)
        }

        if (!isPresent) {
            notebookLogger().debug {
                val loggedRoots = libraryRoots.filter { it.type == LibraryRootTypeId.COMPILED }.takeLast(10).joinToString(separator = "\n") { it.url.presentableUrl }
                "For notebook ${virtualFile.file.name} no dependency '${artifactName}' found among roots of size ${libraryRoots.size}, last roots:\n $loggedRoots"
            }
        }

        return isPresent
    }

    /**
     * KaModules get invalidated during scripting update.
     * However, this method is invoked after changes are made to a Workspace model;
     * hence, it's assumed everything is rebuilt by this time.
     */
    @RequiresReadLock
    override suspend fun filterTypesPresentInIndexes(virtualFile: BackedNotebookVirtualFile, types: Collection<KotlinType>): Collection<KotlinType> {
        val module = virtualFile.findK2WorkspaceModule(project) ?: return emptySet()

        return smartReadAction(project) {
            val allIndexed = module.getIndexedTopLevelClassifiersFiltered(project).map {
                it.fqName?.asString()
            }
            types.filter { it.typeName in allIndexed }
        }
    }

    override fun isScriptPathConsistentWithModel(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: VirtualFileUrl): Boolean {
        return checkSourceIsNotEmpty(virtualFile) && checkArtifactPresentInLibrary(virtualFile, lastCompiledScriptPath)
    }

    override fun isScriptFileConfigurationConsistentWithModel(virtualFile: BackedNotebookVirtualFile, compilationConfiguration: ScriptCompilationConfiguration): Boolean {
        val configurationsCache = project.service<NotebookScriptConfigurationsManager>().cache
        val configurationForNotebook = configurationsCache.getConfigurationForNotebook(virtualFile.file)
        if (configurationForNotebook == null) return false

        /**
         * Here we need to perform 2 steps check:
         *  1. Check that the stored compilation configuration from [NotebookScriptConfigurationsManager] matches the refined one.
         *  2. Check that dependencies from the refined configuration are present in the Workspace library
         *
         *  If any of it is missing, we have a pending update.
         *
         *  NB: A configuration source is a K2 cache for compile configurations,
         *  while the Workspace module contains a dependency used for highlighting.
         */

        // Check the random one since the configuration for any cell will be the same
        val configuration = configurationForNotebook.configuration ?: return false
        val presentInConfigurationSource = compareConfigurationsData(compilationConfiguration, configuration)
        if (!presentInConfigurationSource) return false

        val urlManager = project.workspaceModel.getVirtualFileUrlManager()
        val lastDependencyVFUrl = configurationForNotebook.dependenciesClassPath.lastOrNull()
            ?.toPath()?.toVirtualFileUrl(urlManager) ?: return false
        val presentInModuleDependencies = checkArtifactPresentInLibrary(
            virtualFile, lastDependencyVFUrl
        )

        return presentInModuleDependencies
    }

    // Check only base things as K2 mode could have extra keys present
    private fun compareConfigurationsData(current: ScriptCompilationConfiguration, cached: ScriptCompilationConfiguration): Boolean {
        return current[ScriptCompilationConfiguration.baseClass] == cached[ScriptCompilationConfiguration.baseClass]
                && current[ScriptCompilationConfiguration.implicitReceivers] == cached[ScriptCompilationConfiguration.implicitReceivers]
                && current[ScriptCompilationConfiguration.dependencies] == cached[ScriptCompilationConfiguration.dependencies]
    }
}