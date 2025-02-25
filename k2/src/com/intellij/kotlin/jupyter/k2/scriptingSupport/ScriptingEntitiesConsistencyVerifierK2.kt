// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.ScriptingEntitiesConsistencyVerifier
import com.intellij.kotlin.jupyter.core.scriptingSupport.workSpaceSnapshot
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.workspaceModel.ide.toPath
import org.jetbrains.kotlin.idea.core.script.scriptConfigurationsSourceOfType
import kotlin.script.experimental.api.ScriptCompilationConfiguration

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
        val scriptConfigurationsSource = project.scriptConfigurationsSourceOfType<NotebookScriptConfigurationsSource>()?.data?.get()
        if (scriptConfigurationsSource == null) {
            return false
        }

        return scriptConfigurationsSource.getConfigurationsForNotebook(notebookFile.file)?.isNotEmpty() == true
    }

    override fun isScriptPathConsistentWithModel(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: String): Boolean {
        return checkSourceIsNotEmpty(virtualFile) && getRuntimeLibraryForNotebook(virtualFile)?.roots
            .orEmpty().any { root ->
                root.url.url.contains(lastCompiledScriptPath)
            }
    }

    override fun isScriptFileConfigurationConsistentWithModel(virtualFile: BackedNotebookVirtualFile, compilationConfiguration: ScriptCompilationConfiguration): Boolean {
        val configurationsCache = project.scriptConfigurationsSourceOfType<NotebookScriptConfigurationsSource>()?.data?.get() ?: return true
        val configurationsForNotebookCells = configurationsCache.getConfigurationsForNotebook(virtualFile.file)

        // Check the first one since configuration for any cell will be the same
        val configurationWrapper = configurationsForNotebookCells?.firstOrNull()
        val presentInConfigurationSource = configurationWrapper?.configuration == compilationConfiguration

        val notebookRuntimeDependencyLibrary = getRuntimeLibraryForNotebook(virtualFile)
        val presentInModuleDependencies = notebookRuntimeDependencyLibrary?.roots.orEmpty()
            .any { root ->
                val lastDependencyPath = configurationWrapper?.dependenciesClassPath?.lastOrNull()?.toPath()
                lastDependencyPath == root.url.toPath()
            }

        return presentInConfigurationSource && presentInModuleDependencies
    }
}