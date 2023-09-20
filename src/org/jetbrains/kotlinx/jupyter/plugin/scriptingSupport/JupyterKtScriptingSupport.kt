// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.ScriptingSupport
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsBuilder
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsUpdater
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.JupyterFileType
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterFileEditor
import kotlin.script.experimental.api.valueOrNull

class JupyterKtScriptingSupport(private val project: Project) : ScriptingSupport {
    private val compilerService = JupyterCompilerService.getInstance(project)
    private val editorManager: FileEditorManager? get() = FileEditorManager.getInstance(project)

    override fun afterUpdate() {
        try {
            ScriptDefinitionsManager.getInstance(project).reloadScriptDefinitionsIfNeeded()
            compilerService.afterScriptingUpdate()
        } catch (ex: Exception) {
            if (ex is ProcessCanceledException) {
                ScriptDefinitionsManager.getInstance(project).reloadScriptDefinitionsIfNeeded()
            } else {
                LOG.warn("Post-update: error occurred during reloading of script configurations", ex)
            }
        }
    }

    override fun collectConfigurations(builder: ScriptClassRootsBuilder) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            builder.addTemplateClassesRoots(compilerService.initialClasspath.map { it.absolutePath })
        }

        val editors = editorManager?.allEditors ?: return

        val openFiles = editors.mapNotNull { (it as? JupyterFileEditor)?.getNotebookFile() }
        val notebookFiles = openFiles
            .filter { it.fileType is JupyterFileType }
            .mapNotNull { BackedNotebookVirtualFile.takeIfBacked(it) }
            .filter { it.file.isKotlinNotebook }
        builder.addRootsFromNotebooks(notebookFiles)
    }

    override fun getConfigurationImmediately(file: VirtualFile): ScriptCompilationConfigurationWrapper? {
        if (file !is VirtualFileWindow) return null
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        if (psiFile !is KtFile) return null
        return getConfiguration(project, psiFile)?.valueOrNull()
    }

    override fun isApplicable(file: VirtualFile): Boolean {
        return file.name.endsWith(compilerService.fileSuffix)
    }

    override fun isConfigurationLoadingInProgress(file: KtFile): Boolean {
        return getUpdater(project).isTransactionAboutToHappen()
    }

    private fun ScriptClassRootsBuilder.addRootsFromNotebooks(notebooks: Collection<BackedNotebookVirtualFile>) {
        for (notebook in notebooks) {
            val notebookService = JupyterCompilerService.getForFile(project, notebook)
            addTemplateClassesRoots(notebookService.currentClasspath.map { it.absolutePath })
            addSources(notebookService.currentSourceRoots.map { it.absolutePath })

            warnAboutDependenciesExistence(false)
            try {
                notebookService.scripts().forEach { (file, conf) -> add(file, conf) }
            } catch (e: Throwable) {
                if (e is ProcessCanceledException) throw e
                LOG.error("Notebook injected scripts can't be obtained. Notebook: [$notebook]", e)
            }
            warnAboutDependenciesExistence(true)
        }
    }

    companion object {
        private val LOG = logger<JupyterKtScriptingSupport>()
        private val updateScope = CoroutineScope(Dispatchers.Default)
        private var updateJob: Deferred<*>? = null

        private fun getUpdater(project: Project): ScriptClassRootsUpdater {
            return (ScriptConfigurationManager.getInstance(project) as CompositeScriptConfigurationManager).updater
        }

        fun isInTheTransaction(project: Project) = getUpdater(project).isTransactionAboutToHappen()

        fun update(project: Project) {
            // cache.clear()
            val updater = getUpdater(project)
            updateJob?.cancel()
            if (updater.isTransactionAboutToHappen()) {
                LOG.debug("In the transaction, aborting")
                updateJob?.cancel()
                updateJob = updateScope.async {
                    delay(1000)
                    LOG.debug("Scripting coroutine dispatched")
                    update(project)
                }
                return
            }
            updateJob?.cancel()
            updateJob = null
            LOG.debug("Running scripting support update")
            RecursionManager.doPreventingRecursion("${this::class}: update()", false) {
                updater.invalidateAndCommit()
            }
        }

        fun updateSynchronously(project: Project) {
            val updater = getUpdater(project)
            updater.update {
                updater.invalidate(true)
            }
        }

        fun getConfiguration(project: Project, psiFile: KtFile): ScriptCompilationConfigurationResult? {
            return runReadAction {
                if (!psiFile.isScript()) return@runReadAction null
                val scriptDef = psiFile.findScriptDefinition() ?: return@runReadAction null

                refineScriptCompilationConfiguration(KtFileScriptSource(psiFile), scriptDef, project)
            }
        }

    }
}