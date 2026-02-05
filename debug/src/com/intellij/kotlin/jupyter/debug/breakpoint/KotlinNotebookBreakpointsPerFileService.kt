// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.breakpoint

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookDependencies
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookPerFileSettingsCache
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
    /**
     * Checks if there are any enabled breakpoints in the notebook's dependencies.
     *
     * True if:
     * - The notebook depends on AllLibraries (expensive to check, always return true)
     * - There are enabled breakpoints in files under the dependent module's source roots
     *
     * False in other cases
     */
    fun hasBreakpointsInDependentModule(): Boolean {
        val dependencies = notebookDependencies

        return when (dependencies) {
            is KotlinNotebookDependencies.None -> {
                LOG.debug("No dependencies for notebook ${virtualFile.file.name}, skipping breakpoint check")
                false
            }
            is KotlinNotebookDependencies.AllLibraries -> {
                // Checking all libraries would be expensive, always run debug mode
                LOG.debug("AllLibraries dependency for notebook ${virtualFile.file.name}, assuming breakpoints may exist")
                true
            }
            is KotlinNotebookDependencies.SingleModule -> {
                checkBreakpointsInModule(dependencies)
            }
        }
    }

    fun KotlinNotebookDependencies.getBreakpointsInDependentModule(): List<XBreakpoint<*>> {
        return when (this) {
            is KotlinNotebookDependencies.None -> emptyList()
            is KotlinNotebookDependencies.AllLibraries -> emptyList()
            is KotlinNotebookDependencies.SingleModule -> {
                getBreakpointsInModule(this)
            }
        }
    }

    private val notebookDependencies: KotlinNotebookDependencies
        get() = KotlinNotebookPerFileSettingsCache.getInstance(project)
            .getSettings(virtualFile)
            .notebookDependencies

    private fun checkBreakpointsInModule(dependencies: KotlinNotebookDependencies.SingleModule): Boolean {
        val module = dependencies.findModule(project)
        if (module == null) {
            LOG.debug("Module '${dependencies.moduleName}' not found for notebook ${virtualFile.file.name}")
            return false
        }

        val sourceRoots = module.sourceRoots
        if (sourceRoots.isEmpty()) {
            LOG.debug("No source roots for module '${module.name}'")
            return false
        }

        val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager

        for (breakpoint in breakpointManager.allBreakpoints) {
            if (!breakpoint.isEnabled) continue

            val breakpointFile = breakpoint.sourcePosition?.file ?: continue

            for (sourceRoot in sourceRoots) {
                if (VfsUtil.isAncestor(sourceRoot, breakpointFile, false)) {
                    LOG.debug("Found breakpoint in ${breakpointFile.path} under source root ${sourceRoot.path}")
                    return true
                }
            }
        }

        return false
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
