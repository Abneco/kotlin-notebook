// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.editor.JupyterFileEditor
import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
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
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.SCRIPTING_SUPPORT_TOPIC
import org.jetbrains.kotlinx.jupyter.plugin.util.errorWithAttachments
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.toBackedNotebookFile
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
        scriptingSupportPublisher.onUpdateException(exception)
    }

    override fun onTrivialUpdate() {
        scriptingSupportPublisher.onTrivialUpdate()
    }

    override fun collectConfigurations(builder: ScriptClassRootsBuilder) {
        val editors = editorManager?.allEditors ?: return

        val openFiles = editors.mapNotNull {
            (it as? JupyterFileEditor)?.getNotebookFile()
        }.filter { it.fileType is JupyterFileType }.ifEmpty {
            editors.mapNotNull { BackedNotebookVirtualFile.takeIfBacked(it.file)?.file }
        }
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

        private fun getUpdater(project: Project): ScriptClassRootsUpdater {
            return (ScriptConfigurationManager.getInstance(project) as CompositeScriptConfigurationManager).updater
        }

        fun isInTheTransaction(project: Project) = getUpdater(project).isTransactionAboutToHappen()

        fun updateSynchronously(project: Project) {
            val updater = getUpdater(project)
            updater.update {
                updater.invalidate(true)
            }
        }

        fun getConfiguration(psiFile: KtFile): ScriptCompilationConfigurationResult? {
            val scriptDef = psiFile.findScriptDefinition() ?: return null
            return refineScriptCompilationConfiguration(KtFileScriptSource(psiFile), scriptDef, psiFile.project)
        }

        fun getDefaultConfiguration(psiFile: KtFile): ScriptCompilationConfigurationResult? {
            val virtualFile = psiFile.virtualFile?.safeAs<VirtualFileWindow>()
            if (virtualFile == null) {
                LOG.error("Can't retrieve virtual file window for $psiFile")
                return null
            }

            val notebookVirtualFile = virtualFile.delegate
            val notebookFile = notebookVirtualFile.toBackedNotebookFile()
            if (notebookFile == null) {
                if (notebookVirtualFile is LightVirtualFile) {
                    LOG.warn("Backed notebook file is not yet build for $notebookVirtualFile in injected file $psiFile")
                } else {
                    LOG.error(
                        "Can't retrieve notebook file for $psiFile. " +
                                "Virtual file $notebookVirtualFile is of type ${notebookVirtualFile::class}"
                    )
                }
                return null
            }

            val compilerService = JupyterCompilerService.getForFile(psiFile.project, notebookFile)
            val sourceCode = KtFileScriptSource(psiFile)
            return compilerService.provideDefaultConfiguration(sourceCode)
        }
    }
}