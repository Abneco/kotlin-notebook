// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingService
import com.intellij.kotlin.jupyter.core.ide.handlers.ScriptingSupportUpdater
import com.intellij.kotlin.jupyter.core.projectModel.KotlinNotebookPermanentIndexService
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifacts
import com.intellij.kotlin.jupyter.core.scriptingSupport.definitions.KotlinNotebookScriptDefinitionsWrapper
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.util.NotebookProjectLevelService
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.lang.Language
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
import java.io.File
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.displayName
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

    private val initialClasspath: List<File> by lazy {
       emptyList()
    }

    val scriptDefinitionsWrapper: KotlinNotebookScriptDefinitionsWrapper by lazy {
        KotlinNotebookScriptDefinitionsWrapper.create(
            project,
            ScriptDefinition(
                initialCompileConfiguration,
                evaluationConfiguration
            )
        )
    }

    private val initialCompileConfiguration by lazy {
        getCompilationConfiguration(
            scriptClasspath = initialClasspath,
            compilerArgsConfigurator = DefaultCompilerArgsConfigurator(),
            replCompilerMode = KotlinNotebookApplicationOptions.get().replCompilerMode,
            loggerFactory = DefaultKernelLoggerFactory
        ) {
            ide {
                serializationPluginEnabled(true)
            }
            displayName("Kotlin Notebooks")
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

    val fileExtension: String by lazy {
        initialCompileConfiguration[ScriptCompilationConfiguration.fileExtension] ?: "jupyter.kts"
    }

    val fileSuffix: String by lazy {
        ".$fileExtension"
    }

    val language: Language = Language.findLanguageByID("kotlin")!!

    override fun createInstance(backedFile: BackedNotebookVirtualFile, fileScope: CoroutineScope): JupyterCompilerPerFileService {
        return JupyterCompilerPerFileService(
            this,
            backedFile,
            initialClasspath,
            fileScope
        )
    }

    fun requestScriptingUpdate(): Unit = scriptingSupportUpdateScheduler.requestUpdate()

    fun removeSession(virtualFile: BackedNotebookVirtualFile) {
        mapping.remove(virtualFile.file)?.let { Disposer.dispose(it) }
    }

    fun get(virtualFile: BackedNotebookVirtualFile): JupyterCompilerPerFileService? {
        return mapping[virtualFile.file]
    }

    fun restartHighlighting(files: Collection<VirtualFile>) {
        val notebookFiles = files.filter { it.isKotlinNotebook }.map { BackedNotebookVirtualFile.Companion.takeBackend(it) }
        if (notebookFiles.isEmpty()) {
            return
        }
        coroutineScope.async {
            JupyterKtScriptingSupport.updateSynchronously(project)

            smartReadAction(project) {
                notebookFiles.forEach { file ->
                    NotebookHighlightingService.getForFile(project, file).restartAnalysing()
                }
            }
        }
    }

    private val updateActionHandler = ScriptingSupportUpdater.create(project, this)

    private val scriptingSupportUpdateScheduler = ScriptingSupportUpdateScheduler(
        project,
        updateActionHandler::updateScripts,
        this
    )

    private fun removeRuntimeDependenciesFromIndex() {
        val paths = KotlinNotebookMavenArtifacts.all().mapTo(HashSet()) {
            it.artifact
        }
        KotlinNotebookPermanentIndexService.getInstance(project)
            .removeFromPermanentIndex(paths)
    }

    override fun dispose() {
        super.dispose()
        if (!project.isDisposed) {
            removeRuntimeDependenciesFromIndex()
        }
    }

    companion object {
        fun getInstance(project: Project): JupyterCompilerService = project.service<JupyterCompilerService>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): JupyterCompilerPerFileService {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}
