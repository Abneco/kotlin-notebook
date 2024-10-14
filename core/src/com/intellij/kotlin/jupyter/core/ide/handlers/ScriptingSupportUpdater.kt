// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.ide.handlers

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.editor.JupyterFileEditor
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterKtScriptingSupport.Companion.getConfiguration
import com.intellij.kotlin.jupyter.core.scriptingSupport.k2.KotlinNotebookScriptModel
import com.intellij.kotlin.jupyter.core.scriptingSupport.k2.NotebookScriptDependenciesSource
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.SCRIPTING_SUPPORT_TOPIC
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import kotlinx.coroutines.async
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.k2.K2ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import kotlin.script.experimental.api.valueOrNull


interface ScriptingSupportUpdater : KotlinPluginModeAwareHandler {
    fun updateScripts()

    companion object {
        fun create(project: Project) = createPluginModeAwareInstance(
            project,
            ::K1ScriptingSupportUpdater,
            ::K2ScriptingSupportUpdater,
        )
    }
}

class K1ScriptingSupportUpdater(private val project: Project) : ScriptingSupportUpdater {
    override fun updateScripts() {
        val updater = (ScriptConfigurationManager.getInstance(project) as CompositeScriptConfigurationManager).updater
        RecursionManager.doPreventingRecursion("${this::class}: update()", false) {
            updater.invalidateAndCommit()
        }
    }
}

class K2ScriptingSupportUpdater(private val project: Project) : ScriptingSupportUpdater {
    override fun updateScripts() {
        val editorManager = FileEditorManager.getInstance(project) ?: return
        val scope = KotlinNotebookPluginScope.getForProject(project)
        scope.async {
            updateK2Configurations(editorManager, project)
        }
    }

    private suspend fun updateK2Configurations(editorManager: FileEditorManager, project: Project) {
        if (project.isDisposed) return

        val editors = editorManager.allEditors
        val openFiles = editors.mapNotNull { (it as? JupyterFileEditor)?.getNotebookFile() }
        val publisher = project.messageBus.syncPublisher(SCRIPTING_SUPPORT_TOPIC)

        val notebooks = openFiles
            .filter { it.fileType is JupyterFileType }
            .mapNotNull { BackedNotebookVirtualFile.takeIfBacked(it) }
            .filter { it.file.isKotlinNotebook }

        runCatching {
            updateK2Impl(project, notebooks)
        }.onFailure {
            if (it is ProcessCanceledException || project.isDisposed) {
                return@onFailure
            }
            thisLogger().warn("Exception during update k2 configuration for notebooks", it)
            publisher.onUpdateException(Exception(it))
        }.onSuccess {
            K2ScriptDefinitionProvider.getInstance(project).reloadDefinitionsFromSources()
            publisher.afterUpdate()
        }

        //K2ScriptDefinitionProvider.getInstance(project).reloadDefinitionsFromSources()
    }


    private suspend fun updateK2Impl(project: Project, notebooks: Collection<BackedNotebookVirtualFile>) {
        val scripts = mutableListOf<KotlinNotebookScriptModel>()
        for (notebook in notebooks) {
            val notebookService = JupyterCompilerService.getForFile(project, notebook)
            val perFileScripts = readAction {
                val scriptsToRefine = notebookService.getFilesToRefine()
                scriptsToRefine.map { ktFileScriptSource ->
                    val ktFile = ktFileScriptSource.ktFile

                    val defaultConfiguration = try {
                        getConfiguration(ktFile)?.valueOrNull()?.configuration!!
                    } catch (e: Throwable) {
                        throw e
                    }

                    val source = KtFileScriptSource(ktFile)
                    val refinedConf = notebookService.handleBeforeCompiling(
                        defaultConfiguration,
                        source
                    )

                    KotlinNotebookScriptModel(
                      ktFileScriptSource.virtualFile,
                      ScriptCompilationConfigurationWrapper.FromCompilationConfiguration(
                          source,
                          refinedConf
                      )
                    )
                }
            }

            scripts.addAll(perFileScripts)
        }

        NotebookScriptDependenciesSource.getInstance(project)
            ?.updateDependenciesAndCreateModules(
                scripts
            )
    }

}

