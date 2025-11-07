// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectModel.extensions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskContext

/**
 * Module-specific EP which aim is to adjust how the behavior around dependencies
 * is configured across notebook-plugin submodules.
 *
 * @see [com.intellij.kotlin.jupyter.core.projectModel.JupyterKotlinProjectArtifactsService]
 */
interface KotlinNotebookModuleDependencyBehaviorRefiner {
    /**
     * NB: it's expected to be called only for tasks triggered from within the Kotlin Notebook plugin
     */
    fun refineBuildTaskContext(project: Project, taskContext: ProjectTaskContext): ProjectTaskContext

    /**
     * Determines whether the notification popup should not be shown.
     * This might happen if a change has happened under a debugger session or on a remote machine,
     * so that rebuilding now is not needed.
     */
    fun shouldShowOutdatedDependenciesPopup(project: Project, affectedModules: Collection<Module>): Boolean = false

    companion object {
        private val EP = ExtensionPointName.create<KotlinNotebookModuleDependencyBehaviorRefiner>("com.intellij.kotlin.jupyter.core.moduleDependencyBehaviorRefiner")

        fun refineModuleBuildTaskContext(project: Project, taskContext: ProjectTaskContext): ProjectTaskContext {
            return EP.extensionList.fold(taskContext) { acc, refiner ->
                refiner.refineBuildTaskContext(project, acc)
            }
        }

        fun shouldShowOutdatedDependenciesPopup(project: Project, affectedModules: Collection<Module>): Boolean {
            return EP.extensionList.any { it.shouldShowOutdatedDependenciesPopup(project, affectedModules) }
        }
    }
}