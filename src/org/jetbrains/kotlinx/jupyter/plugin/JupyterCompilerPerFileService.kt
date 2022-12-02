// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin

import com.fasterxml.jackson.databind.node.ArrayNode
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.nullize
import com.intellij.util.io.delete
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.jetbrains.kotlin.idea.core.script.ClasspathToVfsConverter
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.config.defaultGlobalImports
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.NoopMagicsHandler
import org.jetbrains.kotlinx.jupyter.plugin.codeinsight.KotlinNotebookAbstractInlayTypeHintsProvider
import org.jetbrains.kotlinx.jupyter.plugin.file.NotebookHighlightingUtilityObject.ANALYZER_PASS_INJECTED_INFO_HOLDER_KEY
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.scripting.ImpatientNotebookChangeListener
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKotlinPluginScriptClassGetter
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.kotlinx.jupyter.plugin.session.KotlinKernelProcessService
import org.jetbrains.kotlinx.jupyter.plugin.stats.KotlinNotebookPluginUpdater
import org.jetbrains.kotlinx.jupyter.plugin.util.KernelJarsDirProvider
import org.jetbrains.kotlinx.jupyter.plugin.util.allJarsFromDir
import org.jetbrains.kotlinx.jupyter.plugin.util.allSourceRoots
import org.jetbrains.plugins.notebooks.core.impl.file.assertBackedNotebook
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterSource
import java.io.File
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.host.with
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.withUpdatedClasspath

/**
 * This service is created for every Kotlin notebook file
 * and provides a scripting support for injected Kotlin snippets
 * including magics handling, storing dependencies and a list
 * of compiled scripts.
 *
 * @property virtualFile File with Kotlin notebook
 * @property projectService Project service that owns this sub-service
 */
class JupyterCompilerPerFileService(
    private val virtualFile: VirtualFile,
    private val projectService: JupyterCompilerService,
) : Disposable {
    private val compileLock = ReentrantReadWriteLock()
    private val listLock = ReentrantReadWriteLock()
    private val directoryCounter = AtomicInteger(1)
    private val nbInjectionHosts: MutableSet<PsiLanguageInjectionHost> = ContainerUtil.newConcurrentSet() // LoggingList()
    val cellOrdinalToClassName = mutableMapOf<Int, String>()

    private val classesDir: Path by lazy {
        Files.createTempDirectory("kotlin-scripting-jvm-jupyter-kernel")
    }

    private val deserializer = CompiledScriptsSerializer()

    private val magicsProcessor = MagicsProcessor(
        handler = NoopMagicsHandler,
        parseOutCellMarker = true
    )

    private val _currentClasspath: TwoPartsList<File> by lazy {
        TwoPartsList<File>().apply {
            addInitial(projectService.initialClasspath)
        }
    }
    val currentClasspath: List<File> get() = _currentClasspath.getList()

    private val _sourceRoots = TwoPartsList<File>()
    val currentSourceRoots: List<File> get() = _sourceRoots.getList()

    private val additionalDefaultImports: TwoPartsList<String> by lazy {
        TwoPartsList<String>().apply {
            addInitial(defaultGlobalImports)
        }
    }

    private var kernelJarsAdded: Boolean = false
    private val kernelJarsProviders: Collection<KernelJarsDirProvider> = listOf(
        KernelJarsDirProvider {
            // return getPluginResource("kernelJars")
            KotlinKernelProcessService.getInstance().scriptClassPathDir
        },
        KernelJarsDirProvider {
            LOG.warn("Bad way only worked...")
            getSession()?.detectKotlinKernelJarsDir()
        },
    )

    private val implicitsList = KotlinImplicitReceiversList()
    private val classGetter = JupyterKotlinPluginScriptClassGetter(ScriptTemplateWithDisplayHelpers::class) {
        // LOG.warn("Getting implicits list")
        implicitsList
    }

    private val coroutineScope = CoroutineScope(Job())
    private var previousSessionId: String? = null

    private fun syncWithSyntaxDaemonAnalyzer() {
        val project = projectService.project
        project.messageBus.connect(this).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
            override fun daemonCancelEventOccurred(reason: String) {
                //println("Daemon canceled: $reason")
            }

            override fun daemonFinished() { // done analysing?
                //updateCellsAnalysis(false, false) // maybe needed
            }

            override fun daemonStarting(fileEditors: MutableCollection<out FileEditor>) {
                //updateCellsAnalysis(true) // always triggers
            }
        })
    }

    init {
        assertBackedNotebook(virtualFile)

        updateClasspathWithExternalDependencies()
        Disposer.register(projectService, this)
        //syncWithSyntaxDaemonAnalyzer()

        val doc = runReadAction {
            FileDocumentManager.getInstance().getDocument(virtualFile)!!
        }
        if (virtualFile.isKotlinNotebook) {
            doc.addDocumentListener(
                ImpatientNotebookChangeListener(projectService.project, virtualFile),
                projectService
            )
        }
    }

    private fun getSession(): JupyterNotebookSession? {
        return try {
            if (!ApplicationManager.getApplication().isUnitTestMode) {
                JupyterRuntimeService.getInstance(projectService.project).getOrCreateSession(virtualFile)
            } else null
        } catch (e: Throwable) {
            // TODO: show error for user with asking for configuring Python interpreter for the module
            if (e is ProcessCanceledException) return null
            LOG.warn("Cannot create Jupyter session for Kotlin notebook", e)
            null
        }
    }

    private fun updateClasspathWithExternalDependencies() {
        updateClasspathWithKernelJars()

        coroutineScope.async {
            updateClasspathWithProjectArtifacts()
        }
    }

    private fun updateClasspathWithKernelJars() {
        if (kernelJarsAdded) return

        compileLock.write {
            kernelJarsProviders.firstNotNullOfOrNull { provider ->
                provider.getKernelJars()
            }?.let { jarsDir ->
                _currentClasspath.addInitial(jarsDir.allJarsFromDir())
                _sourceRoots.addInitial(KotlinKernelProcessService.getInstance().libSourcesJars)
                kernelJarsAdded = true
            }
        }
    }

    private suspend fun updateClasspathWithProjectArtifacts() {
        val buildService = JupyterKotlinProjectArtifactsService.getInstance(projectService.project)
        val artifacts =
            buildService.getProjectBuildResult()
                ?: buildService.buildProject()
        _currentClasspath.addInitial(artifacts.map { File(it) })
    }

    fun handleBeforeCompiling(
        sourceCode: SourceCode,
        config: ScriptCompilationConfiguration
    ): ScriptCompilationConfiguration {
        val sourceText = runReadAction { sourceCode.text }
        LOG.debug("Before-compiling callback for script: $sourceText")
        updateClasspathWithExternalDependencies()
        val withNewClasspath = config.withUpdatedClasspath(currentClasspath)
        return ScriptCompilationConfiguration(withNewClasspath) {
            hostConfiguration.update {
                it.with {
                    getScriptingClass(classGetter)
                }
            }
            implicitReceivers(implicitsList)
            defaultImports(additionalDefaultImports.getList())
            ide.dependenciesSources(JvmDependency(
                projectService.project.allSourceRoots() + _sourceRoots.getList()
            ))
        }
    }

    fun readInjectionHosts(readAction: (Collection<PsiLanguageInjectionHost>) -> Unit) {
        listLock.read {
            readAction(nbInjectionHosts)
        }
    }

    fun updateInjectionHosts(updateAction: (MutableCollection<PsiLanguageInjectionHost>) -> Unit) {
        listLock.write {
            updateAction(nbInjectionHosts)
        }
    }

    private fun addAsPermanentLibrary(classpath: List<String>, sourceClasspath: List<String>) {
        runWriteAction {
            val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(projectService.project)

            val libraryName = "Permanent Script Dependencies"
            val newLibrary = libraryTable.getLibraryByName(libraryName)
                ?: libraryTable.createLibrary(libraryName)

            val model = newLibrary.modifiableModel
            for (path in classpath) {
                model.addRoot("file://$path", OrderRootType.CLASSES)
            }
            for (path in sourceClasspath) {
                model.addRoot("file://$path", OrderRootType.SOURCES)
            }
            model.commit()
        }
    }

    fun addCompiledSnippet(
        snippetMetadata: EvaluatedSnippetMetadata,
        cellSource: String,
    ) {
        compileLock.writeLock().withLock {
            try {
                KotlinNotebookPluginUpdater.getInstance().pluginUsed()

                val sessionId = ApplicationManager.getApplication().executeOnPooledThread<String?> {
                    getSession()?.sessionId
                }.get()

                if (sessionId != previousSessionId) {
                    LOG.warn("Clearing Kotlin snippets. Previous session ID: $previousSessionId")
                    clearPreviousSnippets()
                    previousSessionId = sessionId
                }

                val nextCounter = directoryCounter.incrementAndGet()

                val lineClassesDir = classesDir.resolve("line_$nextCounter")
                val lineClassesDirAsFile = lineClassesDir.toFile()
                lineClassesDirAsFile.mkdirs()

                val lineSourcesDir = classesDir.resolve("sources_$nextCounter")
                // TODO: compare text in snippet metadata with cell source and add a source file to directory and to the container

                _currentClasspath.addSnippet(ArrayList<File>(snippetMetadata.newClasspath.size + 1).apply {
                    add(lineClassesDirAsFile)
                    snippetMetadata.newClasspath.forEach {
                        add(File(it))
                    }
                })
                _sourceRoots.addSnippet(ArrayList<File>(snippetMetadata.newSources.size + 1).apply {
                    add(lineSourcesDir.toFile())
                    snippetMetadata.newSources.forEach {
                        add(File(it))
                    }
                })
                additionalDefaultImports.addSnippet(snippetMetadata.newImports)

                addAsPermanentLibrary(snippetMetadata.newClasspath, snippetMetadata.newSources)

                val kClassNames = deserializer.deserializeAndSave(snippetMetadata.compiledData, lineClassesDir, lineSourcesDir)
                val classLoader = URLClassLoader(
                    arrayOf(lineClassesDir.toUri().toURL()),
                    (implicitsList.lastOrNull()?.fromClass ?: this::class).java.classLoader
                )
                kClassNames.forEach { className ->
                    LOG.debug("Adding class: $className")
                    val kClass = classLoader.loadClass(className).kotlin
                    implicitsList.addClass(kClass)
                }

                updateCellsAnalysis()
                JupyterKtScriptingSupport.getInstance(projectService.project).update()
            } catch (e: Exception) {
                LOG.error(e)
            }
        }
    }

    private fun getCellCode(cell: PsiElement): String {
        val sourceElement = PsiTreeUtil.getChildOfType(cell, JupyterSource::class.java)
        val source = sourceElement?.text.orEmpty()
        return source.trimStart()
    }

    private fun updateCellsAnalysis() {
        val injectedManager = InjectedLanguageManager.getInstance(projectService.project)
        // update all after exec
        readInjectionHosts {
            it.forEach { host ->
                val properFile = injectedManager.getInjectedPsiFiles(host)
                    ?.firstOrNull()?.first
                properFile?.putUserData(ANALYZER_PASS_INJECTED_INFO_HOLDER_KEY, null)
                host.putUserData(KotlinNotebookAbstractInlayTypeHintsProvider.psiHostHintsRegistry, mutableMapOf())
            }
        }
    }

    fun codeRanges(cell: JupyterPsiCell): CodeRangesResult {
        val code = getCellCode(cell)
        if (looksLikeReplCommand(code)) return CodeRangesResult(CellRanges(null, listOf(TextRange(0, cell.textLength))), true)

        val text = cell.text
        val magicIntervals = magicsProcessor.magicsIntervals(text)

        fun Sequence<CodeInterval>.toRanges() = mapTo(mutableListOf()) {
            TextRange(it.from, it.to)
        }.nullize()

        val codeRanges = magicsProcessor.codeIntervals(text, magicIntervals).toRanges()
        val magicRanges = magicIntervals.toRanges()

        return CodeRangesResult(CellRanges(codeRanges, magicRanges), false)
    }

    data class CodeRangesResult(
        val ranges: CellRanges,
        val isCommand: Boolean,
    )

    private fun clearPreviousSnippets() {
        _currentClasspath.clear()
        additionalDefaultImports.clear()
        implicitsList.clear()
    }

    fun clear() {
        clearPreviousSnippets()

        nbInjectionHosts.clear()
        classesDir.delete(true)
        coroutineScope.cancel()

        ClasspathToVfsConverter.clearCaches()

        val manager = ScriptConfigurationManager.getInstance(projectService.project) as? CompositeScriptConfigurationManager
        manager?.updater?.invalidateAndCommit()
    }

    override fun dispose() {
        clear()
    }

    data class CellRanges(val codeRanges: List<TextRange>?, val magicRanges: List<TextRange>?)

    class TwoPartsList<T>(
        private val initialPart: MutableSet<T> = mutableSetOf(),
        private val snippetsPart: MutableSet<T> = mutableSetOf(),
    ) {
        private val lock = ReentrantReadWriteLock()

        private fun <R> withWriteLock(action: () -> R): R {
            return lock.writeLock().withLock {
                action()
            }
        }

        private fun <R> withReadLock(action: () -> R): R {
            return lock.readLock().withLock {
                action()
            }
        }

        fun clear() {
            withWriteLock { snippetsPart.clear() }
        }

        fun addInitial(items: Collection<T>) {
            withWriteLock { initialPart.addAll(items) }
        }

        fun addSnippet(items: Collection<T>) {
            withWriteLock { snippetsPart.addAll(items) }
        }

        fun getList(): List<T> {
            return withReadLock { (initialPart + snippetsPart).distinct() }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(JupyterCompilerPerFileService::class.java)

        private fun JupyterNotebookSession.detectKotlinKernelJarsDir(): File? {
            val specs = jupyterServer.client.getKernelSpecs()
            val kotlinSpec = specs.firstOrNull { it.displayName == "Kotlin" } ?: return null
            val command = kotlinSpec.metadata?.get("jar_path_detect_command") as? ArrayNode ?: return null
            val commandArgs: List<String> = mutableListOf<String>().apply {
                command.elements().forEachRemaining {
                    add(it.asText())
                }
            }

            val p: Process = try {
                Runtime.getRuntime().exec(commandArgs.toTypedArray())
            } catch (e: Exception) {
                LOG.warn(e)
                return null
            }

            val exitCode = try {
                p.waitFor()
            } catch (e: InterruptedException) {
                LOG.warn(e)
                return null
            }

            if (exitCode != 0) {
                val errorOutput = String(p.errorStream.readAllBytes(), StandardCharsets.UTF_8)
                LOG.warn("Unable to detect kernel JARs location")
                LOG.warn(errorOutput)
                return null
            }

            val processOutput = p.inputStream.readAllBytes()
            val filePath = String(processOutput, StandardCharsets.UTF_8).trim()
            return File(filePath)
        }
    }
}
