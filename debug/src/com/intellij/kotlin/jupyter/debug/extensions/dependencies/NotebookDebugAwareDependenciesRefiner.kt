// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.extensions.dependencies

import com.intellij.debugger.ui.HotSwapUIImpl.SKIP_HOT_SWAP_KEY
import com.intellij.kotlin.jupyter.core.projectModel.extensions.KotlinNotebookModuleDependencyBehaviorRefiner
import com.intellij.kotlin.jupyter.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.debug.util.hasNotebookDebugSession
import com.intellij.kotlin.jupyter.debug.util.notebookDebugFeaturesSupported
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskContext

/**
 * Debug-specific refiner, which aim is to:
 *
 * - Prevent showing a hot swap popup when there's a running debug session
 * - Adjust notification's popup while running under debug session
 */
class NotebookDebugAwareDependenciesRefiner : KotlinNotebookModuleDependencyBehaviorRefiner {
    override fun shouldShowOutdatedDependenciesPopup(
        project: Project,
        affectedModules: Collection<Module>
    ): Boolean {
        return !project.hasNotebookDebugSession
    }

    override fun refineBuildTaskContext(project: Project, taskContext: ProjectTaskContext): ProjectTaskContext {
        return taskContext.apply {
            configureDebugHotSwapOption(project)
        }
    }

    private fun ProjectTaskContext.configureDebugHotSwapOption(project: Project) {
        if (!project.notebookDebugFeaturesSupported) return

        val isDirty = dirtyOutputPaths.isPresent
        if (isDirty) {
            // if there's a running session, we should stick to hot swap
            val hasAnyNotebookSession = KotlinNotebookDebugSessionManager.getInstance(project).hasAnyXDebugSession
            if (hasAnyNotebookSession) return
        }

        withUserData(SKIP_HOT_SWAP_KEY, true)
    }
}