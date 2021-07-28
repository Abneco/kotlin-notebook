package org.jetbrains.kotlinx.jupyter.plugin.scripting

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
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
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.plugin.InjectedElementsList
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.util.component1
import org.jetbrains.kotlinx.jupyter.plugin.util.component2
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.JupyterFileType
import org.jetbrains.plugins.notebooks.jupyter.JupyterLanguage
import kotlin.script.experimental.api.valueOrNull

@Service
class JupyterKtScriptingSupport(private val project: Project) : ScriptingSupport {
    private val compilerService = JupyterCompilerService.getInstance(project)
    private val editorManager: FileEditorManager? get() = FileEditorManager.getInstance(project)
    private val injectedManager = InjectedLanguageManager.getInstance(project)
    private val fileExtension = compilerService.fileExtension

    // private val cache = ConfigurationsCache()

    private val configurationManager: CompositeScriptConfigurationManager
        get() = ScriptConfigurationManager.getInstance(project) as CompositeScriptConfigurationManager

    private val updater
        get() = configurationManager.updater

    fun update() {
        // cache.clear()
        updater.invalidateAndCommit()
    }

    override fun afterUpdate() {
    }

    override fun collectConfigurations(builder: ScriptClassRootsBuilder) {
        val editors = editorManager?.allEditors ?: return

        // Collect all notebook files, get injections from them
        val openFiles = editors.mapNotNull { it.file as? NotebookVirtualFile }
        val notebookFiles = openFiles.filter { it.fileType is JupyterFileType }
        val collector = object : ConfigurationsCollector {
            override fun add(virtualFile: VirtualFile, configuration: ScriptCompilationConfigurationWrapper) {
                builder.add(virtualFile, configuration)
            }
        }
        for (virtualFile in notebookFiles) {
            val psiManager = PsiManager.getInstance(project)
            val psiFile = runReadAction {
                psiManager.findViewProvider(virtualFile)?.getPsi(JupyterLanguage)
            } ?: continue

            val notebookFile = psiFile.containingFile.originalFile.virtualFile
            val injectedFilesPairs = getInjectedFiles(notebookFile)
            for ((psi, _) in injectedFilesPairs) {
                collectConfigurations(psi, collector)
            }
        }
    }

    override fun isApplicable(file: VirtualFile): Boolean {
        return file.extension == fileExtension && file is VirtualFileWindow
    }

    override fun isConfigurationLoadingInProgress(file: KtFile): Boolean {
        return false
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

    private fun collectConfigurations(psiFile: PsiElement, collector: ConfigurationsCollector) {
        if (psiFile !is KtFile) return
        val scriptCompilationConfigurationResult = runReadAction {
            if (!psiFile.isScript()) return@runReadAction null
            val scriptDef = psiFile.findScriptDefinition() ?: return@runReadAction null

            refineScriptCompilationConfiguration(
                KtFileScriptSource(psiFile),
                scriptDef,
                project
            )
        } ?: return
        val scriptCompilationConfiguration = scriptCompilationConfigurationResult.valueOrNull() ?: return
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

    companion object {
        fun getInstance(project: Project) = project.service<JupyterKtScriptingSupport>()
    }
}
