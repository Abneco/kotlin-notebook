// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ultimate.PluginVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.language.kotlin.serialization.serializationPluginEnabled
import org.jetbrains.kotlinx.jupyter.plugin.util.NotebookProjectLevelService
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterRestartKernelListener
import java.io.File
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm

/**
 * [JupyterCompilerService] stores all compiling-related things across the project:
 * mapping from notebooks to file services and "constant" things equal for all
 * the files.
 *
 * @property project This service project
 */
@Service(Service.Level.PROJECT)
class JupyterCompilerService(
    val project: Project,
    coroutineScope: CoroutineScope
) : NotebookProjectLevelService<JupyterCompilerPerFileService>(coroutineScope) {

    init {
        PluginVerifier.verifyUltimatePlugin()
        registerKernelRestartListener()
    }

    private val initialClasspath: List<File> by lazy {
       emptyList()
    }

    private val initialCompileConfiguration by lazy {
        getCompilationConfiguration(
            scriptClasspath = initialClasspath,
            compilerArgsConfigurator = DefaultCompilerArgsConfigurator(),
        ) {
            ide {
                serializationPluginEnabled(true)
            }

            refineConfiguration {
                beforeCompiling { (sourceCode, config, _) ->
                    val virtualFile = (sourceCode as? KtFileScriptSource)?.virtualFile
                    val fileDelegate = (virtualFile as? VirtualFileWindow)?.delegate
                    val notebookFile = fileDelegate?.let(BackedNotebookVirtualFile::takeIfBacked) ?: return@beforeCompiling config.asSuccess()
                    getOrCreate(notebookFile).handleBeforeCompiling(config, sourceCode).asSuccess()
                }
            }
        }
    }

    private val evaluationConfiguration by lazy {
        ScriptEvaluationConfiguration {
            jvm {
                baseClassLoader(this@JupyterCompilerService::class.java.classLoader)
            }
        }
    }

    val scriptDefinition by lazy {
        ScriptDefinition(
            initialCompileConfiguration,
            evaluationConfiguration
        )
    }

    val fileExtension: String by lazy {
        initialCompileConfiguration[ScriptCompilationConfiguration.fileExtension] ?: "jupyter.kts"
    }

    val fileSuffix: String by lazy {
        ".$fileExtension"
    }

    val language = Language.findLanguageByID("kotlin")!!

    override fun createInstance(virtualFile: BackedNotebookVirtualFile, childScope: CoroutineScope): JupyterCompilerPerFileService {
        return JupyterCompilerPerFileService(
            project,
            virtualFile,
            initialClasspath,
            childScope,
            this
        )
    }

    fun requestScriptingUpdate() = scriptingSupportUpdateScheduler.requestUpdate()

    fun removeSession(virtualFile: BackedNotebookVirtualFile) {
        mapping.remove(virtualFile.file)?.let { Disposer.dispose(it) }
    }

    fun get(virtualFile: BackedNotebookVirtualFile): JupyterCompilerPerFileService? {
        return mapping[virtualFile.file]
    }

    fun restartHighlighting(files: Collection<VirtualFile>) {
        val notebookFiles = files.filter { it.isKotlinNotebook }.mapNotNull { BackedNotebookVirtualFile.find(it) }
        if (notebookFiles.isEmpty()) {
            return
        }
        coroutineScope.async {
            JupyterKtScriptingSupport.updateSynchronously(project)

            readAction {
                notebookFiles.forEach { file ->
                    NotebookHighlightingService.getForFile(project, file).restartAnalysing()
                }
            }
        }
    }

    private val scriptingSupportUpdateScheduler = ScriptingSupportUpdateScheduler(
        project,
        ::performScriptingUpdate,
        this
    )

    private fun performScriptingUpdate() {
        val updater = (ScriptConfigurationManager.getInstance(project) as CompositeScriptConfigurationManager).updater
        RecursionManager.doPreventingRecursion("${this::class}: update()", false) {
            updater.invalidateAndCommit()
        }
    }

    private fun registerKernelRestartListener() {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(JupyterRestartKernelListener.TOPIC, object : JupyterRestartKernelListener {
                override fun inActionPerformed(notebookFile: BackedNotebookVirtualFile) {
                    removeSession(notebookFile)
                }
            })
    }

    companion object {
        fun getInstance(project: Project) = project.service<JupyterCompilerService>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): JupyterCompilerPerFileService {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}
