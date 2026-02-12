// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.hotswap

import com.intellij.debugger.impl.DebuggerSession
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookDependencies
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookPerFileSettingsCache
import com.intellij.kotlin.jupyter.core.settings.findModule
import com.intellij.kotlin.jupyter.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.debug.session.KotlinNotebookFileDebugSession
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.xdebugger.impl.hotswap.SourceFileChangeFilter

/**
 * Filter to ignore any source file changes outside targeted modules.
 *
 * Accepts all files under [KotlinNotebookDependencies.SingleModule].
 */
internal class NotebookSourceFileFromDependentModuleFilter(
    private val project: Project,
    private val debuggerSession: DebuggerSession,
) : SourceFileChangeFilter<VirtualFile> {
    private val notebookSession by lazy {
        KotlinNotebookDebugSessionManager.getInstance(project).getByDebugProcessOrNull(debuggerSession.process)
    }
    private val dependenciesScope by lazy {
        val session = notebookSession ?: return@lazy null
        getModuleScope(session)
    }

    override suspend fun isApplicable(change: VirtualFile): Boolean {
        val scope = dependenciesScope ?: return false
        return readAction { scope.contains(change) }
    }

    private fun getModuleScope(notebookSession: KotlinNotebookFileDebugSession): GlobalSearchScope? {
        val settings = KotlinNotebookPerFileSettingsCache.getInstance(project)
                           .getCachedSettings(notebookSession.virtualFile.file) ?: return null

        return when (val deps = settings.notebookDependencies) {
            is KotlinNotebookDependencies.SingleModule -> {
                val module = deps.findModule(project) ?: return null
                GlobalSearchScope.moduleWithDependenciesScope(module)
            }
            is KotlinNotebookDependencies.AllLibraries -> null
            KotlinNotebookDependencies.None -> null
        }
    }
}
