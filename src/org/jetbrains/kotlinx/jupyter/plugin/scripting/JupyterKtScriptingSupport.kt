// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scripting

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.util.runIf
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.ScriptingSupport
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsBuilder
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsUpdater
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookReferenceFinder.CELL_CLASS_NAME
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookReferenceFinder.traverseChildrenAndSearch
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookUsagesContributorFactory.dfPrefix
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.ReferenceSearchStrategy
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.isCompiledCellClassDeclaration
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.isItGeneratedNameInsideLambdaCall
import org.jetbrains.kotlinx.jupyter.plugin.util.switchForScriptsAsEntities
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.JupyterFileType
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterFileEditor
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterNotebook
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
        val notebookFiles = openFiles.mapNotNull { if (it.fileType is JupyterFileType) BackedNotebookVirtualFile(it) else null }
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
        return getUpdater(project).isInTransaction()
    }

    private fun ScriptClassRootsBuilder.addRootsFromNotebooks(notebooks: Collection<BackedNotebookVirtualFile>) {
        for (notebook in notebooks) {
            val notebookService = JupyterCompilerService.getForFile(project, notebook)
            addTemplateClassesRoots(notebookService.currentClasspath.map { it.absolutePath })
            addSources(notebookService.currentSourceRoots.map { it.absolutePath })

            switchForScriptsAsEntities(
                on = {
                    warnAboutDependenciesExistence(false)
                    try {
                        notebookService.scripts().forEach { (file, conf) -> add(file, conf) }
                    } catch (e: Throwable) {
                        if (e is ProcessCanceledException) throw e
                        LOG.error("Notebook injected scripts can't be obtained. Notebook: [$notebook]", e)
                    }
                    warnAboutDependenciesExistence(true)
                },
                off = {}
            )
        }
    }

    companion object {
        private val LOG = logger<JupyterKtScriptingSupport>()

        private fun getUpdater(project: Project): ScriptClassRootsUpdater {
            return (ScriptConfigurationManager.getInstance(project) as CompositeScriptConfigurationManager).updater
        }

        fun update(project: Project) {
            // cache.clear()
            val updater = getUpdater(project)
            if (updater.isInTransaction()) return
            LOG.info("Running scripting support update")
            RecursionManager.doPreventingRecursion("${this::class}: update()", false) {
                updater.invalidateAndCommit()
            }
        }

        fun getConfiguration(project: Project, psiFile: KtFile): ScriptCompilationConfigurationResult? {
            return runReadAction {
                if (!psiFile.isScript()) return@runReadAction null
                val scriptDef = psiFile.findScriptDefinition() ?: return@runReadAction null

                refineScriptCompilationConfiguration(KtFileScriptSource(psiFile), scriptDef, project)
            }
        }

        fun searchForElementDeclarationOrUsages(
            project: Project,
            target: PsiElement,
            virtualFile: VirtualFile,
            searchStrategy: ReferenceSearchStrategy
        ): MutableSet<PsiElement>? {
            if (!virtualFile.isKotlinNotebook) return null
            val injectedManager = InjectedLanguageManager.getInstance(project)
            val foundData = mutableSetOf<PsiElement>()
            val asPsiFile = PsiManager.getInstance(project).findFile(virtualFile)
            val notebookCells = (asPsiFile?.children?.first() as? JupyterNotebook)?.psiCellList ?: return null
            val ordinalMap = JupyterCompilerService.getForFile(project, BackedNotebookVirtualFile(virtualFile)).cellOrdinalToClassName
            val injectionManager = InjectedLanguageManager.getInstance(project)
            val targetHost = injectionManager.getInjectionHost(target.containingFile)
            val targetClassName = runIf(searchStrategy == ReferenceSearchStrategy.REFERENCES) {
                targetHost?.let {
                    val name = ordinalMap[notebookCells.indexOf(it)]
                    if (it.getUserData(CELL_CLASS_NAME) == null && name != null) it.putUserData(CELL_CLASS_NAME, name)
                    name
                }
            }
            if (target.parent == null) return null // means we have inconsistent notebook state
            val targetContainingFile = target.containingFile

            var isLocalSearch = if (searchStrategy == ReferenceSearchStrategy.REFERENCES) {
                targetHost?.getUserData(CELL_CLASS_NAME) == null && !isCompiledCellClassDeclaration(target)
            } else false
            if (!isLocalSearch && targetContainingFile.name.contains(dfPrefix)) {
                isLocalSearch = isItGeneratedNameInsideLambdaCall(target, target)
            }
            //println("isLocalSearch: $isLocalSearch for ${target.text}")
            val properContainer = if (isLocalSearch) listOf(injectionManager.getInjectionHost(targetContainingFile)) else notebookCells

            return runReadAction {
                for (ind in properContainer.indices) {
                    val gotHost = properContainer[ind] ?: continue
                    val host = if (isLocalSearch) gotHost else notebookCells[ind]
                    val firstInjectedFileInfo = injectedManager.getInjectedPsiFiles(host)?.firstOrNull() ?: continue
                    val psiFile = firstInjectedFileInfo.first ?: continue
                    // should second part still be there?
                    if (target.parent == null) { // means we have inconsistent notebook state
                        break
                    }
                    if (psiFile !is KtFile || (psiFile == targetContainingFile && searchStrategy == ReferenceSearchStrategy.DECLARATION)) continue
                    val scriptBlock = psiFile.findChildrenByClass(KtScript::class.java).firstOrNull()?.blockExpression ?: continue
                    val elements = mutableListOf<NavigatablePsiElement>()
                    val possibleClassName = ordinalMap[ind]
                    traverseChildrenAndSearch(
                        injectionManager, host, targetClassName ?: possibleClassName, scriptBlock, target, searchStrategy,
                        elements
                    )

                    if (searchStrategy == ReferenceSearchStrategy.DECLARATION) {
                        val first = elements.firstOrNull()
                        if (first != null) {
                            foundData.add(first)
                            break
                        }
                    } else foundData += elements
                }

                return@runReadAction foundData
            }
        }
    }
}