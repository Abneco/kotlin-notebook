// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.updater

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.JupyterExecutionListener
import com.intellij.kotlin.jupyter.core.ide.handlers.ScriptingSupportUpdater
import com.intellij.kotlin.jupyter.core.ide.handlers.UpdaterConstructorData
import com.intellij.kotlin.jupyter.core.logging.KotlinNotebookLoggerFactory
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.scriptingSupport.definitions.notebookScriptDefinitionWrapper
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.SCRIPTING_SUPPORT_TOPIC
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import com.intellij.kotlin.jupyter.k2.scriptingSupport.KotlinNotebookScriptModel
import com.intellij.kotlin.jupyter.k2.scriptingSupport.NotebookScriptConfigurationsManager
import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.platform.backend.workspace.workspaceModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlin.idea.core.script.k2.asCompilationConfiguration
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.implicitReceivers

internal class K2ScriptingSupportUpdater(updaterConstructorData: UpdaterConstructorData) : ScriptingSupportUpdater {
    companion object {
        private val LOG = KotlinNotebookLoggerFactory.getInstance(K2ScriptingSupportUpdater::class)
    }

    private val project = updaterConstructorData.project
    private val disposalMutex = Mutex()

    init {
        val parentDisposable = updaterConstructorData.parentDisposable
        JupyterExecutionListener.register(parentDisposable, object : JupyterExecutionListener {
            override suspend fun sessionWillTerminate(notebookFile: BackedNotebookVirtualFile) {
                clearRuntimeDependenciesFor(notebookFile)
            }
        })
    }

    /**
     * Handler for exceptions in a structured concurrency.
     * NB: [CancellationException] is never delivered to [CoroutineExceptionHandler] by design —
     * it is filtered out in [kotlinx.coroutines.JobSupport]
     */
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        if (project.isDisposed) {
            return@CoroutineExceptionHandler
        }
        LOG.warn("Exception during update k2 configuration for notebooks", e)
    }

    override fun updateScripts() {
        val editorManager = FileEditorManager.getInstance(project) ?: return
        val scope = KotlinNotebookPluginScope.getForProject(project)

        scope.launch(exceptionHandler) {
            if (project.isDisposed) return@launch

            withUpdateNotification {
                val updatedNotebooks = updateK2Configurations(editorManager, project)
                requestDefinitionReloadIfNecessary()
                updatedNotebooks
            }
        }
    }

    /**
     * Executes [block] and notifies the [SCRIPTING_SUPPORT_TOPIC] listeners about the status
     */
    private inline fun withUpdateNotification(block: () -> Collection<BackedNotebookVirtualFile>?) {
        var updateFailure: Throwable? = null
        var updatedNotebooks: Collection<BackedNotebookVirtualFile>? = null
        try {
            updatedNotebooks = block()
        } catch (t: Throwable) {
            updateFailure = t
            throw t
        } finally {
            project.messageBus.syncPublisher(SCRIPTING_SUPPORT_TOPIC).afterUpdate(updatedNotebooks, updateFailure)
        }
    }

    /**
     * Prefer lazy reload
     * Note: calling 'currentDefinitions' might invoke computations
     */
    private fun requestDefinitionReloadIfNecessary() {
        val notebookDefinition = project.notebookScriptDefinitionWrapper
        val definitions = ScriptDefinitionProviderImpl.getInstance(project).currentDefinitions
        if (definitions.contains(notebookDefinition.compilationScriptDefinition)) return

        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }

    override fun ensureScriptConfiguration(project: Project, ktFile: KtFile) {
        JupyterCompilerService.getInstance(ktFile.project).getDefaultConfiguration(ktFile.virtualFile)
    }
    /**
     * Clears [org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult] for a particular [BackedNotebookVirtualFile]
     */
    private fun clearRuntimeDependenciesFor(notebookFile: BackedNotebookVirtualFile) {
        val scope = KotlinNotebookPluginScope.getForProject(project)
        scope.async {
            val service = project.serviceIfCreated<NotebookScriptConfigurationsManager>() ?: return@async
            disposalMutex.withLock {
                service.clearNotebookLibraryDependencies(notebookFile)
            }
        }
    }

    /**
     * Stream-lined update of a workspace model performed in phases in one update cycle:
     * - Phase 1: Update a workspace model with new configurations for all notebooks
     * - Phase 2: After smart mode is enabled (~ indexing is not running), refine directly
     */
    private suspend fun updateK2Configurations(editorManager: FileEditorManager, project: Project): Collection<BackedNotebookVirtualFile>? {
        if (project.isDisposed) return null

        val editors = editorManager.allEditors
        val openFiles = editors.mapNotNull { it.file }

        val notebooks = openFiles
            .filter { it.fileType is JupyterFileType }
            .mapNotNull { it.toKotlinNotebookBackedFile() }

        val notebooksToUpdate = notebooks
            .filter { JupyterCompilerService.getForFile(project, it).needsConfigurationUpdate }

        if (notebooksToUpdate.isEmpty()) {
            LOG.debug("No notebooks to update")
            return notebooks
        }

        // Phase 1: main workspace model update
        updateK2Impl(project, notebooksToUpdate)

        // Let indexing proceed
        project.waitForSmartMode()

        // Phase 2: process pending implicit receivers
        coroutineScope {
            for (notebook in notebooksToUpdate) {
                launch {
                    JupyterCompilerService.getForFile(project, notebook).processPostUpdateReceivers()
                }
            }
        }

        return notebooks
    }

    private suspend fun updateK2Impl(project: Project, notebooks: Collection<BackedNotebookVirtualFile>) {
        val notebookNames = notebooks.joinToString(", ") { it.file.name }
        LOG.debug("Performing update for notebooks: $notebookNames")

        val scripts = mutableSetOf<KotlinNotebookScriptModel>()
        for (notebook in notebooks) {
            val refinedConfiguration = notebook.getRefinedConfiguration()
            val virtualFile = notebook.file

            /**
             * Refined configuration contains only checked receivers from [JupyterCompilerPerFileService].
             */
            val stableClasses = refinedConfiguration[ScriptCompilationConfiguration.implicitReceivers]
            LOG.debug {
                "Stable implicit receivers for notebook '${virtualFile.name}': ${stableClasses?.map { it.typeName }}"
            }

            val storedConfiguration = NotebookScriptConfigurationsManager.getInstance(project).getKotlinScriptEntity(notebook.file)
                ?.configurationId
                ?.let { project.workspaceModel.currentSnapshot.resolve(it) }
                ?.data
                ?.asCompilationConfiguration()

            // skip if exists
            if (storedConfiguration == refinedConfiguration) {
                continue
            }

            val scriptModel = KotlinNotebookScriptModel(
                virtualFile,
                ScriptCompilationConfigurationWrapper(
                    VirtualFileScriptSource(virtualFile),
                    refinedConfiguration
                )
            )
            scripts += scriptModel
        }

        if (scripts.isEmpty()) {
            LOG.info("No scripts to refine found, skipping update. Notebooks: $notebookNames")
            return
        }

        project.serviceAsync<NotebookScriptConfigurationsManager>().updateConfigurations(scripts)
    }

    private suspend fun BackedNotebookVirtualFile.getRefinedConfiguration(): ScriptCompilationConfiguration {
        val compilerService = JupyterCompilerService.getForFile(project, this)
        return compilerService.getRefinedConfigurationForPublishing()
    }
}