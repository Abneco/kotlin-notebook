// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.breakpoint

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookDependencies
import com.intellij.kotlin.jupyter.core.settings.findModule
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.util.sourceRoots

/**
 * Per-file service for manipulating breakpoint-related logic
 * inside the notebook's debug session.
 *
 */
class KotlinNotebookBreakpointsPerFileService(
    private val project: Project,
    virtualFile: BackedNotebookVirtualFile,
    scope: CoroutineScope
) : NotebookPerFileChildService(virtualFile, scope) {
    fun KotlinNotebookDependencies.getBreakpointsInDependentModule(): List<XBreakpoint<*>> {
        return when (this) {
            is KotlinNotebookDependencies.None -> emptyList()
            is KotlinNotebookDependencies.AllLibraries -> emptyList()
            is KotlinNotebookDependencies.SingleModule -> {
                getBreakpointsInModule(this)
            }
        }
    }

    private fun getBreakpointsInModule(dependencies: KotlinNotebookDependencies.SingleModule): List<XBreakpoint<*>> {
        val module = dependencies.findModule(project) ?: return emptyList()
        val sourceRoots = module.sourceRoots
        if (sourceRoots.isEmpty()) return emptyList()

        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
        val breakpoints = mutableListOf<XBreakpoint<*>>()

        for (breakpoint in breakpointManager.allBreakpoints) {
            if (!breakpoint.isEnabled) continue

            val breakpointFile = breakpoint.sourcePosition?.file ?: continue

            for (sourceRoot in sourceRoots) {
                if (VfsUtil.isAncestor(sourceRoot, breakpointFile, false)) {
                    breakpoints.add(breakpoint)
                    break
                }
            }
        }

        return breakpoints
    }

    companion object {
        private val LOG = notebookLogger()
    }
}
