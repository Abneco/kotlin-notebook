// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.scriptingSupport

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.helper.notebookFileOrNull
import com.intellij.kotlin.jupyter.core.scriptingSupport.IndexAwareScriptDefinitionsLoadRequestor
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.SCRIPTING_SUPPORT_TOPIC
import com.intellij.kotlin.jupyter.core.util.errorWithAttachments
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.toBackedNotebookFile
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k1.ScriptClassRootsUpdater
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.k1.configuration.ScriptingSupport
import org.jetbrains.kotlin.idea.core.script.k1.ucache.ScriptClassRootsBuilder
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.io.path.absolutePathString
import kotlin.script.experimental.api.valueOrNull

class JupyterKtScriptingSupport(private val project: Project) : ScriptingSupport {
    private val compilerService = JupyterCompilerService.getInstance(project)
    private val editorManager: FileEditorManager? get() = FileEditorManager.getInstance(project)
    private val indexAwareScriptDefinitionsRequestor = IndexAwareScriptDefinitionsLoadRequestor(project)
    private val scriptingSupportPublisher = project.messageBus.syncPublisher(SCRIPTING_SUPPORT_TOPIC)

    override fun afterUpdate() {
        try {
            scriptingSupportPublisher.afterUpdate()
            indexAwareScriptDefinitionsRequestor.reloadDefinitions()
        } catch (ex: Exception) {
            if (ex is ProcessCanceledException) {
                indexAwareScriptDefinitionsRequestor.reloadDefinitions()
            } else {
                LOG.warn("Post-update: error occurred during reloading of script configurations", ex)
            }
        }
    }

    override fun onUpdateException(exception: Exception) {
        scriptingSupportPublisher.afterUpdate(updateFailure = exception)
    }

    override fun onTrivialUpdate() {
        scriptingSupportPublisher.onTrivialUpdate()
    }

    override fun collectConfigurations(builder: ScriptClassRootsBuilder) {
        val editors = editorManager?.allEditors ?: return
        val notebookFiles = editors.mapNotNull { it?.notebookFileOrNull }.filter { it.file.isKotlinNotebook }
        builder.addRootsFromNotebooks(notebookFiles)
    }

    override fun getConfigurationImmediately(file: VirtualFile): ScriptCompilationConfigurationWrapper? {
        if (file !is VirtualFileWindow) return null
        val psiFile = file.findPsiFile(project) ?: return null
        if (psiFile !is KtFile) return null
        val conf = getDefaultConfiguration(psiFile)?.valueOrNull()
        if (conf == null) {
            LOG.errorWithAttachments("Can't retrieve fast configuration", Attachment(psiFile.name, psiFile.text))
        }
        return conf
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
            addTemplateClassesRoots(notebookService.currentClasspath.map { it.absolutePathString() })
            addSources(notebookService.currentSourceRoots.map { it.absolutePathString() })

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

        private fun getUpdater(project: Project): ScriptClassRootsUpdater = ScriptConfigurationManager.getInstance(project).updater

        fun updateSynchronously(project: Project) {
            val updater = getUpdater(project)
            updater.update {
                updater.invalidate(true)
            }
        }

        @OptIn(UnsafeCastFunction::class)
        fun getDefaultConfiguration(psiFile: KtFile): ScriptCompilationConfigurationResult? {
            val virtualFile = psiFile.virtualFile?.safeAs<VirtualFileWindow>()
            if (virtualFile == null) {
                LOG.error("Can't retrieve virtual file window for $psiFile")
                return null
            }

            val notebookVirtualFile = virtualFile.delegate
            val notebookFile = notebookVirtualFile.toBackedNotebookFile()

            val compilerService = JupyterCompilerService.getForFile(psiFile.project, notebookFile)
            val sourceCode = KtFileScriptSource(psiFile)
            return compilerService.provideDefaultConfiguration(sourceCode)
        }
    }
}