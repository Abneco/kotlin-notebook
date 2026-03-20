// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.project

import com.intellij.jupyter.core.jupyter.helper.notebookFileOrNull
import com.intellij.kotlin.jupyter.core.settings.actions.promptSessionShutdownIfNeeded
import com.intellij.kotlin.jupyter.core.util.getCurrentEditorOrNull
import com.intellij.kotlin.jupyter.k2.scriptingSupport.KotlinNotebookScriptEntitySource
import com.intellij.kotlin.jupyter.k2.settings.KotlinNotebookK2ProjectOptionsProvider
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.workspaceModel
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerPluginsScriptConfigurationListener
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity

class NotebookK2PostStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        removeStalePermanentLibrary(project)
        removeOrphanedNotebookLibraryEntities(project)
        installProjectOptionsListener(project)
    }

    /**
     * Removes the legacy "Permanent Script Dependencies" project-level library.
     * Let's keep this method for the time-being
     */
    private suspend fun removeStalePermanentLibrary(project: Project) {
        val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        edtWriteAction {
            val library = libraryTable.getLibraryByName(PERMANENT_SCRIPT_DEPENDENCIES_LIBRARY_NAME) ?: return@edtWriteAction
            libraryTable.removeLibrary(library)
        }
    }

    /**
     * Removes library entities that are no longer referenced by any notebook.
     * Prevents unbounded growth of cached dependencies in the workspace model.
     */
    private suspend fun removeOrphanedNotebookLibraryEntities(project: Project) {
        val workspaceModel = project.workspaceModel
        val snapshot = workspaceModel.currentSnapshot

        val orphanedEntities = snapshot.entities(KotlinScriptLibraryEntity::class.java)
            .filter { it.entitySource is KotlinNotebookScriptEntitySource && it.usedInScripts.isEmpty() }
            .toList()

        if (orphanedEntities.isEmpty()) return

        workspaceModel.update("Removing orphaned Kotlin Notebook library entities") { storage ->
            for (entity in orphanedEntities) {
                storage.resolve(entity.symbolicId)?.let { storage.removeEntity(it) }
            }
        }
    }

    private fun installProjectOptionsListener(project: Project) {
        KotlinNotebookK2ProjectOptionsProvider.getInstance(project).apply {
            addListener(object : KotlinNotebookK2ProjectOptionsProvider.Listener {
                override fun onCompilerPluginsChanged() {
                    val editor = project.getCurrentEditorOrNull() ?: return
                    val notebookFile = editor.notebookFileOrNull ?: return
                    promptSessionShutdownIfNeeded(project, notebookFile) {
                        project.notifyCompilerPluginsSettingsChanged()
                    }
                }
            }, this)
        }
    }

    private fun Project.notifyCompilerPluginsSettingsChanged() {
        messageBus.syncPublisher(KotlinCompilerPluginsScriptConfigurationListener.TOPIC).scriptConfigurationsChanged()
    }

    companion object {
        private const val PERMANENT_SCRIPT_DEPENDENCIES_LIBRARY_NAME = "Permanent Script Dependencies"
    }
}
