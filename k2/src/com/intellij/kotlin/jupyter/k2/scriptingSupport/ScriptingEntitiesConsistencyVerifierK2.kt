// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.projectModel.kotlin.getIndexedTopLevelClassifiersFiltered
import com.intellij.kotlin.jupyter.core.scriptingSupport.ScriptingEntitiesConsistencyVerifier
import com.intellij.kotlin.jupyter.core.scriptingSupport.workSpaceSnapshot
import com.intellij.kotlin.jupyter.k2.projectModel.findK2WorkspaceModule
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
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
    private fun getNotebookModulesLibraryEntities(): Sequence<LibraryEntity> {
        return project.workSpaceSnapshot.entitiesBySource {
            it is KotlinNotebookScriptEntitySource
        }.filterIsInstance<LibraryEntity>()
    }

    private fun getRuntimeLibraryForNotebook(notebookFile: BackedNotebookVirtualFile): LibraryEntity? {
        return getNotebookModulesLibraryEntities().firstOrNull {
            it.name == notebookFile.file.toK2RuntimeDependencyLibraryName(project)
        }
    }

    private fun checkSourceIsNotEmpty(notebookFile: BackedNotebookVirtualFile): Boolean {
        val scriptConfigurationsSource = project.service<NotebookScriptConfigurationsManager>().cache
        return scriptConfigurationsSource.getConfigurationForNotebook(notebookFile.file) != null
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

    /**
     * Only COMPILED roots influence analysis, so we're checking that the necessary artifacts are already present in the model
     * before analysis could start.
     */
    override fun isScriptPathConsistentWithModel(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: VirtualFileUrl): Boolean {
        val artifactName = lastCompiledScriptPath.fileName

        return checkSourceIsNotEmpty(virtualFile) && getRuntimeLibraryForNotebook(virtualFile)?.roots
            .orEmpty().any { root ->
                root.type == LibraryRootTypeId.COMPILED && root.url.presentableUrl.endsWith(artifactName)
            }
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

        val notebookRuntimeDependencyLibrary = getRuntimeLibraryForNotebook(virtualFile)
        val urlManager = project.workspaceModel.getVirtualFileUrlManager()
        val lastDependencyVFUrl = configurationForNotebook.dependenciesClassPath.lastOrNull()
            ?.toPath()?.toVirtualFileUrl(urlManager)
        val presentInModuleDependencies = notebookRuntimeDependencyLibrary?.roots.orEmpty()
            .any { root ->
                lastDependencyVFUrl == root.url
            }

        return presentInModuleDependencies
    }

    // Check only base things as K2 mode could have extra keys present
    private fun compareConfigurationsData(current: ScriptCompilationConfiguration, cached: ScriptCompilationConfiguration): Boolean {
        return current[ScriptCompilationConfiguration.baseClass] == cached[ScriptCompilationConfiguration.baseClass]
                && current[ScriptCompilationConfiguration.implicitReceivers] == cached[ScriptCompilationConfiguration.implicitReceivers]
                && current[ScriptCompilationConfiguration.dependencies] == cached[ScriptCompilationConfiguration.dependencies]
    }
}