// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.ScriptingEntitiesConsistencyVerifier
import com.intellij.kotlin.jupyter.core.scriptingSupport.workSpaceSnapshot
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.workspaceModel.ide.toPath
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

    override fun isScriptPathConsistentWithModel(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: String): Boolean {
        return checkSourceIsNotEmpty(virtualFile) && getRuntimeLibraryForNotebook(virtualFile)?.roots
            .orEmpty().any { root ->
                root.url.url.contains(lastCompiledScriptPath)
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
        val presentInModuleDependencies = notebookRuntimeDependencyLibrary?.roots.orEmpty()
            .any { root ->
                val lastDependencyPath = configurationForNotebook.dependenciesClassPath.lastOrNull()?.toPath()
                lastDependencyPath == root.url.toPath()
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