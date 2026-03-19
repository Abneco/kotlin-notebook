// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.ide.handlers.ScriptingSupportUpdater
import com.intellij.kotlin.jupyter.core.scriptingSupport.definitions.KotlinNotebookScriptDefinitionsWrapper
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.util.NotebookProjectLevelService
import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrSelf
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import com.intellij.lang.Language
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlinx.jupyter.compiler.DefaultCompilerArgsConfigurator
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.config.getCompilationConfiguration
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.compilerOptions
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
    project: Project,
    coroutineScope: CoroutineScope
) : NotebookProjectLevelService<JupyterCompilerPerFileService>(project, coroutineScope) {
    private val scriptingForceUpdater: NotebookScriptingForceUpdateRequestor by lazy {
        NotebookScriptingForceUpdateRequestor.create(project)
    }

    // Make it possible to use AtomicReference as a "cache" we can reset by setting the value to null
    private inline fun <T: Any> AtomicReference<T?>.getOrSet(crossinline initializer: () -> T): T =
        updateAndGet { existing -> existing ?: initializer() }!!

    // Current values of the script definitions. If these are `null`, you must call `getOrSet` to update them correctly.
    private val scriptDefinitionsWrapperValue: AtomicReference<KotlinNotebookScriptDefinitionsWrapper?> = AtomicReference()
    private val initialCompileConfigurationValue: AtomicReference<ScriptCompilationConfiguration?> = AtomicReference()

    private val initialClasspath: List<Path> by lazy {
       emptyList()
    }

    /**
     * Some changes to the script definition (like compiler arguments) require a new script definition as
     * they cannot be updated after the compiler session has started.
     *
     * Calling this method will remove any current definition and lazily create a new one. Generally, they
     * are cached by already open Notebooks, so the new definition will only be fetched for new files
     * or if the kernel is restarted.
     */
    fun resetScriptDefinition() {
        scriptDefinitionsWrapperValue.set(null)
        initialCompileConfigurationValue.set(null)
    }

    val scriptDefinitionsWrapper: KotlinNotebookScriptDefinitionsWrapper
        get() {
            return scriptDefinitionsWrapperValue.getOrSet {
                // This logic should be kept side effect free, as long as it might
                // be called multiple times and even simultaneously.
                KotlinNotebookScriptDefinitionsWrapper.create(
                    project,
                    ScriptDefinition(
                        initialCompileConfiguration,
                        evaluationConfiguration
                    )
                )
            }
        }

    private val initialCompileConfiguration: ScriptCompilationConfiguration
        get() {
            return initialCompileConfigurationValue.getOrSet {
                // This logic should be kept side effect free, as long as it might
                // be called multiple times and even simultaneously.
                getCompilationConfiguration(
                    scriptClasspath = initialClasspath.map { it.toFile() },
                    compilerArgsConfigurator = DefaultCompilerArgsConfigurator(jvmTargetVersion = defaultRuntimeProperties.jvmTargetForSnippets),
                    replCompilerMode = KotlinNotebookApplicationOptions.get().replCompilerMode,
                    loggerFactory = DefaultKernelLoggerFactory
                ) {
                    ide {
                        serializationPluginEnabled(true)
                    }
                    displayName("Kotlin Notebooks")
                    compilerOptions.update { oldOptions ->
                        val extraOptions = KotlinNotebookProjectOptionsProvider.getInstance(project).extraCompilerArguments.toList()
                        buildSet {
                            oldOptions?.let { addAll(it) }
                            addAll(extraOptions)
                        }.toList().takeIf { it.isNotEmpty() }
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

    override fun createInstance(virtualFile: BackedNotebookVirtualFile, fileScope: CoroutineScope): JupyterCompilerPerFileService {
        return JupyterCompilerPerFileService(
            this,
            virtualFile,
            initialClasspath,
            fileScope
        )
    }

    fun requestScriptingUpdate(): Unit = scriptingSupportUpdateScheduler.requestUpdate()

    fun get(virtualFile: BackedNotebookVirtualFile): JupyterCompilerPerFileService? {
        return mapping[virtualFile.file]
    }

    fun restartHighlighting(files: Collection<VirtualFile>) {
        val notebookFiles = files.filter { it.isKotlinNotebook }.map { BackedNotebookVirtualFile.takeBackend(it) }
        if (notebookFiles.isEmpty()) {
            return
        }
        coroutineScope.async {
            scriptingForceUpdater.forceUpdateScripting(notebookFiles)
        }
    }

    fun getDefaultConfiguration(virtualFile: VirtualFile): ScriptCompilationConfigurationResult? {
        val topLevelFile = virtualFile.getTopLevelFileOrSelf()
        val notebookFile = topLevelFile.toKotlinNotebookBackedFile()
        if (notebookFile == null) {
            return null
        }

        val sourceCode = VirtualFileScriptSource(topLevelFile)
        val compilerService = getForFile(project, notebookFile)
        return compilerService.provideDefaultConfiguration(sourceCode)
    }

    fun ensureScriptConfiguration(project: Project, ktFile: KtFile) {
        updateActionHandler.ensureScriptConfiguration(project, ktFile)
    }

    private val updateActionHandler = ScriptingSupportUpdater.create(project, this)

    private val scriptingSupportUpdateScheduler = ScriptingSupportUpdateScheduler(
        project,
        updateActionHandler::updateScripts,
        this
    )

    override fun dispose() {
        super.dispose()
    }

    companion object {
        fun getInstance(project: Project): JupyterCompilerService = project.service<JupyterCompilerService>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): JupyterCompilerPerFileService {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}
