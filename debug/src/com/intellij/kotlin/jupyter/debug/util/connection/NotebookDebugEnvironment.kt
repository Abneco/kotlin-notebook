// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.util.connection

import com.intellij.debugger.DefaultDebugEnvironment
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookDependencies
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookPerFileSettingsCache
import com.intellij.kotlin.jupyter.core.settings.findModule
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

/**
 * Custom debug environment for Kotlin Notebooks that extends the search scope
 * to include project modules configured as notebook dependencies.
 *
 * This allows breakpoints in project files (not just notebook cells) to work
 * when debugging Kotlin Notebooks.
 */
internal class NotebookDebugEnvironment(
    executionEnvironment: ExecutionEnvironment,
    state: RunProfileState,
    remoteConnection: RemoteConnection,
    pollTimeout: Long,
    private val notebookFile: BackedNotebookVirtualFile,
    private val project: Project,
) : DefaultDebugEnvironment(executionEnvironment, state, remoteConnection, pollTimeout) {
    companion object {
        private val LOG = notebookLogger()
    }

    override fun getSearchScope(): GlobalSearchScope {
        val baseScope = super.getSearchScope()
        val moduleScope = createModuleSearchScope()
        if (moduleScope == null) {
            LOG.info("No module scope for notebook ${notebookFile.file.name}, using base scope")
            return baseScope
        }
        LOG.info("Adding module scope to debug search scope for notebook ${notebookFile.file.name}: $moduleScope")
        return baseScope.union(moduleScope)
    }

    private fun createModuleSearchScope(): GlobalSearchScope? {
        val settings = KotlinNotebookPerFileSettingsCache.getInstance(project)
                           .getCachedSettings(notebookFile.file) ?: return null

        return when (val deps = settings.notebookDependencies) {
            is KotlinNotebookDependencies.SingleModule -> {
                val module = deps.findModule(project) ?: return null
                GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, true)
            }
            is KotlinNotebookDependencies.AllLibraries -> {
                GlobalSearchScope.allScope(project)
            }
            KotlinNotebookDependencies.None -> null
        }
    }
}
