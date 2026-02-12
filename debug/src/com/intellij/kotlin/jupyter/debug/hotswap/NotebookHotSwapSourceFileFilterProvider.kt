// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.hotswap

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.hotswap.HotSwapSourceFileFilterProvider
import com.intellij.kotlin.jupyter.debug.util.connection.DebugConnectionUtility.NOTEBOOK_PROCESS_MARKER_KEY
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.impl.hotswap.SourceFileChangeFilter

/**
 * Filters out all files that are not part of the current notebook module dependency.
 *
 * This filter is being created during [DebuggerSession] initialization,
 * so it uses marker to identify if [DebugProcess] is bound to a notebook session.
 */
internal class NotebookHotSwapSourceFileFilterProvider : HotSwapSourceFileFilterProvider {
    override fun provideFiltersForSession(debuggerSession: DebuggerSession): List<SourceFileChangeFilter<VirtualFile>> {
        val notebookDebugSession = isNotebookDebugSession(debuggerSession)
        if (!notebookDebugSession) {
            return emptyList()
        }

        return listOf(
            NotebookSourceFileFromDependentModuleFilter(debuggerSession.project, debuggerSession)
        )
    }

    private fun isNotebookDebugSession(debuggerSession: DebuggerSession): Boolean {
        return debuggerSession.process.getUserData(NOTEBOOK_PROCESS_MARKER_KEY) == true
    }
}
