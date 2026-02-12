// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.scriptingSupport.ScriptingEntitiesConsistencyVerifier
import com.intellij.kotlin.jupyter.k2.project.model.findK2WorkspaceEntityDependencies
import com.intellij.kotlin.jupyter.k2.project.model.findK2WorkspaceScriptEntities
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.idea.core.script.k2.asCompilationConfiguration
import org.jetbrains.kotlin.idea.core.script.k2.configurations.toVirtualFileUrl
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.jvm.util.toClassPathOrEmpty

internal class ScriptingEntitiesConsistencyVerifierFactoryK2 : ScriptingEntitiesConsistencyVerifier.Factory {
    override fun create(project: Project): ScriptingEntitiesConsistencyVerifier {
        return ScriptingEntitiesConsistencyVerifierK2(project)
    }
}

private class ScriptingEntitiesConsistencyVerifierK2(
    private val project: Project,
) : ScriptingEntitiesConsistencyVerifier {
    val fileUrlManager: VirtualFileUrlManager
        get() = project.workspaceModel.getVirtualFileUrlManager()

    private fun getDependencyRootsForNotebook(notebookFile: BackedNotebookVirtualFile): Collection<VirtualFileUrl> {
        val snapshot = project.workspaceModel.currentSnapshot
        val kotlinScriptEntity = notebookFile.findK2WorkspaceScriptEntities(project.workspaceModel)
                                     .singleOrNull() ?: return emptyList()

        return kotlinScriptEntity.dependencies
            .mapNotNull { snapshot.resolve(it) }
            .flatMapTo(mutableSetOf()) {
                it.classes
            }
    }

    private fun checkSourceIsNotEmpty(notebookFile: BackedNotebookVirtualFile): Boolean =
        NotebookScriptConfigurationsManager.getInstance(project).getKotlinScriptEntity(notebookFile.file) != null

    /**
     * Only COMPILED roots influence analysis, so we're checking that the necessary artifacts are already present in the model
     * before analysis could start.
     */
    private fun checkArtifactPresentInLibrary(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: VirtualFileUrl): Boolean {
        val artifactName = lastCompiledScriptPath.fileName

        val jars = getDependencyRootsForNotebook(virtualFile)
        val isPresent = jars.any { root -> root.presentableUrl.endsWith(artifactName) }

        if (!isPresent) {
            notebookLogger().debug {
                val loggedRoots = jars.toList().takeLast(10).joinToString(separator = "\n") { it.presentableUrl }
                "For notebook ${virtualFile.file.name} no dependency '${artifactName}' found among roots of size ${jars.size}, last roots:\n $loggedRoots"
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
    override suspend fun filterTypesPresentInIndexes(
        virtualFile: BackedNotebookVirtualFile,
        types: Collection<KotlinType>,
    ): Collection<KotlinType> {
        val dependencies = virtualFile.findK2WorkspaceEntityDependencies(project)
        val scope = GlobalSearchScopesCore.directoriesScope(project, true, *dependencies.toTypedArray())

        return smartReadAction(project) {
            types.filter { type ->
                KotlinFullClassNameIndex[type.typeName, project, scope].isNotEmpty()
            }
        }
    }

    override fun isScriptPathConsistentWithModel(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: VirtualFileUrl): Boolean {
        return checkSourceIsNotEmpty(virtualFile) && checkArtifactPresentInLibrary(virtualFile, lastCompiledScriptPath)
    }

    override fun isScriptFileConfigurationConsistentWithModel(
        virtualFile: BackedNotebookVirtualFile, compilationConfiguration: ScriptCompilationConfiguration,
    ): Boolean {
        val configuration =
            NotebookScriptConfigurationsManager.getInstance(project).getKotlinScriptEntity(virtualFile.file)
                ?.configurationEntity
                ?.let { project.workspaceModel.currentSnapshot.resolve(it) }
                ?.data
                ?.asCompilationConfiguration() ?: return false

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
        val presentInConfigurationSource = compareConfigurationsData(compilationConfiguration, configuration)
        if (!presentInConfigurationSource) return false

        val lastDependencyVFUrl =
            configuration[ScriptCompilationConfiguration.dependencies].toClassPathOrEmpty().lastOrNull()?.path?.toVirtualFileUrl(
                fileUrlManager) ?: return false
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