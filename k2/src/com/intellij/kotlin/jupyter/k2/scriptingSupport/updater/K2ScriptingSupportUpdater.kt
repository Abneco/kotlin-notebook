// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.updater

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.JupyterSessionEventsListener
import com.intellij.kotlin.jupyter.core.ide.handlers.ScriptingSupportUpdater
import com.intellij.kotlin.jupyter.core.ide.handlers.UpdaterConstructorData
import com.intellij.kotlin.jupyter.core.logging.KotlinNotebookLoggerFactory
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerPerFileService
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.SCRIPTING_SUPPORT_TOPIC
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import com.intellij.kotlin.jupyter.k2.scriptingSupport.KotlinNotebookScriptModel
import com.intellij.kotlin.jupyter.k2.scriptingSupport.NotebookScriptConfigurationsManager
import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalScriptModuleStateModificationEvent
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import java.util.concurrent.CancellationException
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.valueOrNull

internal class K2ScriptingSupportUpdater(updaterConstructorData: UpdaterConstructorData) : ScriptingSupportUpdater {
    companion object {
        private val LOG = KotlinNotebookLoggerFactory.getInstance(K2ScriptingSupportUpdater::class)
    }

    private val project = updaterConstructorData.project
    private val disposalMutex = Mutex()

    init {
        val parentDisposable = updaterConstructorData.parentDisposable
        JupyterSessionEventsListener.register(parentDisposable, object : JupyterSessionEventsListener {
            override suspend fun sessionWillTerminate(notebookFile: BackedNotebookVirtualFile) {
                clearRuntimeDependenciesFor(notebookFile)
            }
        })
    }

    /**
     * Special handler for a structured concurrency
     */
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        if (e is CancellationException || project.isDisposed) {
            return@CoroutineExceptionHandler
        }
        LOG.warn("Exception during update k2 configuration for notebooks", e)
        project.messageBus.syncPublisher(SCRIPTING_SUPPORT_TOPIC).onUpdateException(e)
    }

    override fun updateScripts() {
        val editorManager = FileEditorManager.getInstance(project) ?: return
        val scope = KotlinNotebookPluginScope.getForProject(project)

        scope.launch(exceptionHandler) {
            if (project.isDisposed) return@launch

            val updatedNotebooks = updateK2Configurations(editorManager, project)
            ScriptDefinitionProviderImpl.getInstance(project).notifyDefinitionsChanged()
            project.messageBus.syncPublisher(SCRIPTING_SUPPORT_TOPIC).afterUpdate(updatedNotebooks)
        }
    }

    override fun ensureScriptConfiguration(project: Project, ktFile: KtFile) {
        JupyterCompilerService.getInstance(ktFile.project).getDefaultConfiguration(ktFile.virtualFile)
    }

    /**
     * Clears [org.jetbrains.kotlin.idea.core.script.k2.configurations.ScriptConfiguration] for a particular [BackedNotebookVirtualFile]
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
        } else {
            updateK2Impl(project, notebooksToUpdate)
        }
        return notebooks
    }

    private suspend fun updateK2Impl(project: Project, notebooks: Collection<BackedNotebookVirtualFile>) {
        val notebookNames = notebooks.joinToString(", ") { it.file.name }
        LOG.debug("Performing update for notebooks: $notebookNames")

        val scripts = mutableMapOf<KotlinNotebookScriptModel, KtFile>()
        val configurationCache = project.serviceAsync<NotebookScriptConfigurationsManager>().cache
        for (notebook in notebooks) {
            val notebookService = JupyterCompilerService.getForFile(project, notebook)
            val perFileScripts = readAction {
                val scriptsToRefine = notebookService.getFilesToRefine().firstOrNull { it.virtualFile.isValid }
                if (scriptsToRefine == null) {
                    return@readAction null
                }

                // refine only once as they are the same per notebook
                val refinedConfiguration = try {
                    val anyKtFile = scriptsToRefine.ktFile
                    JupyterCompilerPerFileService.getConfiguration(anyKtFile)?.configuration!!
                } catch (e: Throwable) {
                    throw e
                }

                /**
                 * Refined configuration contains only checked receivers from [JupyterCompilerPerFileService].
                 */
                val stableClasses = refinedConfiguration[ScriptCompilationConfiguration.implicitReceivers]
                LOG.debug {
                    "Stable implicit receivers for notebook '${notebook.file.name}': ${stableClasses?.map { it.typeName }}"
                }

                val storedConfiguration = configurationCache[notebook.file]
                    ?.scriptConfiguration?.valueOrNull()?.configuration

                // skip if exists
                if (storedConfiguration == refinedConfiguration) {
                    return@readAction null
                }

                KotlinNotebookScriptModel(
                    scriptsToRefine.virtualFile,
                    ScriptCompilationConfigurationWrapper.FromCompilationConfiguration(
                        scriptsToRefine,
                        refinedConfiguration
                    )
                ) to scriptsToRefine.ktFile
            }

            if (perFileScripts != null) {
                scripts[perFileScripts.first] = perFileScripts.second
            }
        }

        if (scripts.isEmpty()) {
            LOG.info("No scripts to refine found, skipping update. Notebooks: $notebookNames")
            return
        }

        project.serviceAsync<NotebookScriptConfigurationsManager>().updateConfigurations(scripts.keys)
        // Might be the case our own strategy is needed
        updateWorkspaceConfiguration(scripts.keys).join()
    }

    private fun updateWorkspaceConfiguration(notebookModels: Collection<KotlinNotebookScriptModel>): Job {
        val configurationManager = NotebookScriptConfigurationsManager.getInstance(project)

        return KotlinNotebookPluginScope.getForProject(project).launch {
            val updatedConfigurationsWithSdk = notebookModels.associate {
                it.virtualFile to configurationManager.get(it.virtualFile)
            }.filter { it.value != null }.mapValues { it.value!! }

            configurationManager.updateWorkspaceModel(updatedConfigurationsWithSdk)

            edtWriteAction {
                project.publishGlobalModuleStateModificationEvent()
                project.publishGlobalScriptModuleStateModificationEvent()
            }

            ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
            HighlightingSettingsPerFile.getInstance(project).incModificationCount()
        }
    }
}