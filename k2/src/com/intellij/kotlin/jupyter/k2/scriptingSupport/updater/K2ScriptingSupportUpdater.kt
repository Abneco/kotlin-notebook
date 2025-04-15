// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.updater

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JUPYTER_SESSION_LIFETIME_TOPIC
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterSessionLifetimeListener
import com.intellij.kotlin.jupyter.core.ide.handlers.ScriptingSupportUpdater
import com.intellij.kotlin.jupyter.core.ide.handlers.UpdaterConstructorData
import com.intellij.kotlin.jupyter.core.logging.KotlinNotebookLoggerFactory
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterKtScriptingSupport
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.SCRIPTING_SUPPORT_TOPIC
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import com.intellij.kotlin.jupyter.k2.scriptingSupport.KotlinNotebookScriptModel
import com.intellij.kotlin.jupyter.k2.scriptingSupport.NotebookScriptConfigurationsManager
import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.k2.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.idea.core.script.k2.K2ScriptDefinitionProvider
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

    init {
        val parentDisposable = updaterConstructorData.parentDisposable
        project.messageBus.connect(parentDisposable)
            .subscribe(
                JUPYTER_SESSION_LIFETIME_TOPIC,
                object: JupyterSessionLifetimeListener {
                    override fun sessionWillTerminate(notebookFile: BackedNotebookVirtualFile) {
                        clearRuntimeDependenciesFor(notebookFile)
                    }
                }
            )
    }

    /**
     * Special handler for a structured concurrency
     */
    private val exceptionHandler = CoroutineExceptionHandler { context, e ->
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
            updateK2Configurations(editorManager, project)

            K2ScriptDefinitionProvider.getInstance(project).reloadDefinitionsFromSources()
            project.messageBus.syncPublisher(SCRIPTING_SUPPORT_TOPIC).afterUpdate()
        }
    }

    /**
     * Clears [org.jetbrains.kotlin.idea.core.script.k2.ScriptConfiguration] for a particular [BackedNotebookVirtualFile]
     */
    private fun clearRuntimeDependenciesFor(notebookFile: BackedNotebookVirtualFile) {
        val scope = KotlinNotebookPluginScope.getForProject(project)
        scope.async {
            project.serviceIfCreated<NotebookScriptConfigurationsManager>()
              ?.clearNotebookLibraryDependencies(
                  notebookFile
              )
        }
    }

    private suspend fun updateK2Configurations(editorManager: FileEditorManager, project: Project) {
        if (project.isDisposed) return

        val editors = editorManager.allEditors
        val openFiles = editors.mapNotNull { it.file }
        val publisher = project.messageBus.syncPublisher(SCRIPTING_SUPPORT_TOPIC)

        val notebooks = openFiles
            .filter { it.fileType is JupyterFileType }
            .mapNotNull { it.toKotlinNotebookBackedFile() }
            .filter { JupyterCompilerService.getForFile(project, it).needsConfigurationUpdate }

        // Early return
        if (notebooks.isEmpty()) {
            publisher.afterUpdate()
            return
        }

        updateK2Impl(project, notebooks)
    }

    private suspend fun updateK2Impl(project: Project, notebooks: Collection<BackedNotebookVirtualFile>) {
        val scripts = mutableMapOf<KotlinNotebookScriptModel, KtFile>()
        for (notebook in notebooks) {
            val notebookService = JupyterCompilerService.getForFile(project, notebook)
            val perFileScripts = readAction {
                val scriptsToRefine = notebookService.getFilesToRefine()
                if (scriptsToRefine.isEmpty()) {
                    return@readAction emptyList()
                }

                // refine only once as they are the same per notebook
                val refinedConfiguration = try {
                    val anyKtFile = scriptsToRefine.first().ktFile
                    JupyterKtScriptingSupport.getConfiguration(anyKtFile)?.valueOrNull()?.configuration!!
                } catch (e: Throwable) {
                    throw e
                }

                /**
                 * Data race preventing trick:
                 * pass the refined (new) one configuration to the cache,
                 * but set implicitReceivers from the stable one,
                 * thus, libraryRoots and classes of the module will be up to date with stable receivers.
                 */
                val stableConfiguration = notebookService.stableConfiguration

                val configurationWithStableReceivers = ScriptCompilationConfiguration(refinedConfiguration) {
                    val stableClasses = stableConfiguration[implicitReceivers]
                    if (stableClasses != null) {
                        implicitReceivers(stableClasses)
                    }
                }

                scriptsToRefine.map { ktFileScriptSource ->
                    KotlinNotebookScriptModel(
                        ktFileScriptSource.virtualFile,
                        ScriptCompilationConfigurationWrapper.FromCompilationConfiguration(
                            ktFileScriptSource,
                            configurationWithStableReceivers
                        )
                    ) to ktFileScriptSource.ktFile
                }
            }

            scripts.putAll(perFileScripts)
        }

        project.serviceAsync<NotebookScriptConfigurationsManager>().updateConfigurations(scripts.keys)
        DefaultScriptResolutionStrategy.getInstance(project).execute(*scripts.values.toTypedArray())
    }
}