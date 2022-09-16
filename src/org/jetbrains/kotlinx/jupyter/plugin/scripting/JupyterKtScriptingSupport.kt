package org.jetbrains.kotlinx.jupyter.plugin.scripting

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.ScriptingSupport
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsBuilder
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookReferenceFinder.traverseChildrenAndSearch
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.ReferenceSearchStrategy
import org.jetbrains.plugins.notebooks.jupyter.JupyterFileType
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterFileEditor
import kotlin.script.experimental.api.valueOrNull

@Service
class JupyterKtScriptingSupport(private val project: Project) : ScriptingSupport {
    private val compilerService = JupyterCompilerService.getInstance(project)
    private val editorManager: FileEditorManager? get() = FileEditorManager.getInstance(project)
    private val fileExtension get() = compilerService.fileExtension

    private val configurationManager: CompositeScriptConfigurationManager
        get() = ScriptConfigurationManager.getInstance(project) as CompositeScriptConfigurationManager

    private val updater
        get() = configurationManager.updater

    fun update() {
        // cache.clear()
        if (updater.isInTransaction()) return
        logger<JupyterKtScriptingSupport>().warn("Running scripting support update")
        RecursionManager.doPreventingRecursion("${this::class}: update()", false) {
            updater.invalidateAndCommit()
        }
    }

    override fun afterUpdate() {
    }

    override fun collectConfigurations(builder: ScriptClassRootsBuilder) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            builder.addTemplateClassesRoots(compilerService.initialClasspath.map { it.absolutePath })
        }

        val editors = editorManager?.allEditors ?: return

        val openFiles = editors.mapNotNull { (it as? JupyterFileEditor)?.getNotebookFile() }
        val notebookFiles = openFiles.filter { it.fileType is JupyterFileType }
        builder.addRootsFromNotebooks(notebookFiles)
    }

    override fun getConfigurationImmediately(file: VirtualFile): ScriptCompilationConfigurationWrapper? {
        if (file !is VirtualFileWindow) return null
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        if (psiFile !is KtFile) return null
        return getConfiguration(psiFile)?.valueOrNull()
    }

    override fun isApplicable(file: VirtualFile): Boolean {
        return file.extension == fileExtension && file is VirtualFileWindow
    }

    override fun isConfigurationLoadingInProgress(file: KtFile): Boolean {
        return updater.isInTransaction()
    }

    private fun ScriptClassRootsBuilder.addRootsFromNotebooks(notebooks: Collection<VirtualFile>) {
        for (notebook in notebooks) {
            val notebookService = JupyterCompilerService.getForFile(project, notebook)
            addTemplateClassesRoots(notebookService.currentClasspath.map { it.absolutePath })
            addSources(notebookService.currentSourceRoots.map { it.absolutePath })
        }
    }

    private fun getConfiguration(psiFile: KtFile): ScriptCompilationConfigurationResult? {
        return runReadAction {
            if (!psiFile.isScript()) return@runReadAction null
            val scriptDef = psiFile.findScriptDefinition() ?: return@runReadAction null

            refineScriptCompilationConfiguration(
                KtFileScriptSource(psiFile),
                scriptDef,
                project
            )
        }
    }

    fun searchForElementDeclarationOrUsages(target: PsiElement, virtualFile: VirtualFile, searchStrategy: ReferenceSearchStrategy): Array<PsiElement>? {
        if (!virtualFile.isKotlinNotebook) return null
        val fileService = compilerService.get(virtualFile)
        val injectedManager = InjectedLanguageManager.getInstance(project)
        val foundData = mutableSetOf<PsiElement>()

        return runReadAction {
            fileService.readInjectionHosts { hosts ->
                for (host in hosts) {
                    val firstInjectedFileInfo = injectedManager.getInjectedPsiFiles(host)?.firstOrNull() ?: continue
                    val psiFile = firstInjectedFileInfo.first ?: continue
                    if (psiFile !is KtFile || (psiFile == target.containingFile && searchStrategy == ReferenceSearchStrategy.DECLARATION)) continue
                    val scriptBlock = psiFile.findChildrenByClass(KtScript::class.java).firstOrNull()?.blockExpression ?: continue
                    val elements = mutableListOf<NavigatablePsiElement>()
                    traverseChildrenAndSearch(host, scriptBlock, target, searchStrategy, elements)

                    if (searchStrategy == ReferenceSearchStrategy.DECLARATION) {
                        val first = elements.firstOrNull()
                        if (first != null) {
                            foundData.add(first)
                            break
                        }
                    } else foundData += elements
                }
            }

            return@runReadAction foundData.toTypedArray()
        }

    }


    companion object {
        fun getInstance(project: Project) = project.service<JupyterKtScriptingSupport>()
    }
}
