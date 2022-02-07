package org.jetbrains.kotlinx.jupyter.plugin.scripting

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.ScriptingSupport
import org.jetbrains.kotlin.idea.core.script.ucache.ScriptClassRootsBuilder
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.plugin.InjectedElementsList
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.JupyterFileType
import kotlin.script.experimental.api.valueOrNull

@Service
class JupyterKtScriptingSupport(private val project: Project) : ScriptingSupport {
    private val compilerService = JupyterCompilerService.getInstance(project)
    private val editorManager: FileEditorManager? get() = FileEditorManager.getInstance(project)
    private val injectedManager = InjectedLanguageManager.getInstance(project)
    private val fileExtension = compilerService.fileExtension

    private val cache = ConfigurationsCache()

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
        val editors = editorManager?.allEditors ?: return

        // builder.addInitialRoots()

        val openFiles = editors.mapNotNull { it.file as? NotebookVirtualFile }
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

    private fun ScriptClassRootsBuilder.addInitialRoots() {
        val compilerService = JupyterCompilerService.getInstance(project)
        addTemplateClassesRoots(compilerService.initialClasspath.map { it.absolutePath })
    }

    private fun ScriptClassRootsBuilder.addRootsFromNotebooks(notebooks: Collection<NotebookVirtualFile>) {
        for (notebook in notebooks) {
            val notebookService = JupyterCompilerService.getForFile(project, notebook)
            addTemplateClassesRoots(notebookService.currentClasspath.map { it.absolutePath })
        }
    }

    private fun getInjectedFiles(virtualFile: VirtualFile): InjectedElementsList {
        if (virtualFile !is NotebookVirtualFile) return emptyList()
        val fileService = compilerService.get(virtualFile)

        return runReadAction {
            fileService.withInjectionHosts { hosts ->
                hosts.flatMap { host -> injectedManager.getInjectedPsiFiles(host).orEmpty() }
            }
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

    private fun collectConfigurations(psiFile: PsiElement, collector: ConfigurationsCollector) {
        if (psiFile !is KtFile) return
        val scriptCompilationConfiguration = getConfiguration(psiFile)?.valueOrNull() ?: return
        collector.add(psiFile.virtualFile, scriptCompilationConfiguration)
    }

    private interface ConfigurationsCollector {
        fun add(virtualFile: VirtualFile, configuration: ScriptCompilationConfigurationWrapper)
    }

    private class ConfigurationsCache(
        private val cache: MutableMap<String, ScriptCompilationConfigurationWrapper> = hashMapOf(),
    ) : MutableMap<String, ScriptCompilationConfigurationWrapper> by cache, ConfigurationsCollector {
        override fun add(virtualFile: VirtualFile, configuration: ScriptCompilationConfigurationWrapper) {
            cache[virtualFile.path] = configuration
        }
    }

    private class BuilderConfigurationsCollector(private val builder: ScriptClassRootsBuilder) : ConfigurationsCollector {
        override fun add(virtualFile: VirtualFile, configuration: ScriptCompilationConfigurationWrapper) {
            builder.add(virtualFile, configuration)
        }
    }

    private class CompositeConfigurationsCollector(private vararg val collectors: ConfigurationsCollector) : ConfigurationsCollector {
        override fun add(virtualFile: VirtualFile, configuration: ScriptCompilationConfigurationWrapper) {
            collectors.forEach {
                it.add(virtualFile, configuration)
            }
        }
    }

    companion object {
        fun getInstance(project: Project) = project.service<JupyterKtScriptingSupport>()
    }
}
