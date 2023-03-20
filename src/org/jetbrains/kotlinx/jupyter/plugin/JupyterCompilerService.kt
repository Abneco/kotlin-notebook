// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ultimate.PluginVerifier
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.plugin.session.KotlinKernelProcessService
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterRestartKernelListener
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.fileExtension
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
@Service
class JupyterCompilerService(val project: Project) : Disposable {
    private val mapping: MutableMap<VirtualFile, JupyterCompilerPerFileService> = ConcurrentHashMap()

    init {
        PluginVerifier.verifyUltimatePlugin()
        registerKernelRestartListener()
    }

    val initialClasspath: List<File> by lazy {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            KotlinKernelProcessService.getInstance().ideJars
        } else emptyList()
    }

    private val initialCompileConfiguration by lazy {
        getCompilationConfiguration(
            scriptClasspath = initialClasspath,
            compilerArgsConfigurator = DefaultCompilerArgsConfigurator(),
        ) {
            refineConfiguration {
                beforeCompiling { (sourceCode, config, _) ->
                    val virtualFile = (sourceCode as? KtFileScriptSource)?.virtualFile
                    val fileDelegate = (virtualFile as? VirtualFileWindow)?.delegate
                    val notebookFile = fileDelegate?.let(BackedNotebookVirtualFile::takeIfBacked) ?: return@beforeCompiling config.asSuccess()
                    getOrCreate(notebookFile).handleBeforeCompiling(sourceCode, config).asSuccess()
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

    fun getOrCreate(virtualFile: BackedNotebookVirtualFile): JupyterCompilerPerFileService {
        return mapping.getOrPut(virtualFile.file) { JupyterCompilerPerFileService(project, virtualFile, initialClasspath, this) }
    }

    fun removeSession(virtualFile: BackedNotebookVirtualFile) {
        mapping.remove(virtualFile.file)?.let { Disposer.dispose(it) }
    }

    fun get(virtualFile: BackedNotebookVirtualFile): JupyterCompilerPerFileService? {
        return mapping[virtualFile.file]
    }

    val needToUpdateImplicitsReceiversIfAny: Boolean get() {
        var shouldUpdate = false
        mapping.forEach { (_, u) -> if (u.hasPendingUpdates || u.loadReceiverClassesIfAny()) shouldUpdate = true }
        return shouldUpdate
    }

    fun afterScriptingUpdate() {
        mapping.forEach { (_, u) -> u.afterScriptingUpdate() }
    }

    private fun registerKernelRestartListener() {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(JupyterRestartKernelListener.TOPIC, object : JupyterRestartKernelListener {
                override fun inActionPerformed(notebookFile: BackedNotebookVirtualFile) {
                    removeSession(notebookFile)
                }
            })
    }

    override fun dispose() {
    }

    companion object {
        internal const val SCRIPT_DEPENDENCIES_LIBRARY_NAME = "Permanent Script Dependencies"

        fun getInstance(project: Project) = project.service<JupyterCompilerService>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): JupyterCompilerPerFileService {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}
