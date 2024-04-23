// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.delete
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.jetbrains.kotlin.idea.core.script.ClasspathToVfsConverter
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.config.addBaseClass
import org.jetbrains.kotlinx.jupyter.config.defaultGlobalImports
import org.jetbrains.kotlinx.jupyter.plugin.editor.notifications.NotebookNotificationUtility
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService.Companion.buildProjectAndGetLibraries
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.KotlinNotebookPermanentIndexService
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifactsDownloader
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.NotebookCodeSnippetsChangeListener
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.SCRIPTING_SUPPORT_TOPIC
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.ScriptingSupportAfterUpdateListener
import org.jetbrains.kotlinx.jupyter.plugin.settings.getSelectedKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.statistics.usages.KotlinNotebookPluginUpdater
import org.jetbrains.kotlinx.jupyter.plugin.util.ComputableWithName
import org.jetbrains.kotlinx.jupyter.plugin.util.ExecutedOnceBackgroundTask
import org.jetbrains.kotlinx.jupyter.plugin.util.NotebookPerFileChildService
import org.jetbrains.kotlinx.jupyter.plugin.util.allSourceRoots
import org.jetbrains.kotlinx.jupyter.plugin.util.anyOf
import org.jetbrains.kotlinx.jupyter.plugin.util.errorUnderDebug
import org.jetbrains.kotlinx.jupyter.plugin.util.getInjectedKtFiles
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.runSafelyTyped
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.host.with
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.withUpdatedClasspath

/**
 * This service is created for every Kotlin notebook file
 * and provides a scripting support for injected Kotlin snippets
 * including magics handling, storing dependencies, and a list
 * of compiled scripts.
 *
 * @property project       Target Project instance
 * @property virtualFile   File with Kotlin notebook
 * @param initialClasspath Initial classpath to use
 * @param parent           Parent Disposable
 */
class JupyterCompilerPerFileService(
    private val project: Project,
    virtualFile: BackedNotebookVirtualFile,
    initialClasspath: List<File>,
    scope: CoroutineScope,
    parent: Disposable
) : NotebookPerFileChildService(virtualFile, scope) {
    private val scriptsChangePublisher get() =
        project.messageBus.syncPublisher(NotebookCodeSnippetsChangeListener.TOPIC)

    private var isDisposed = false

    // This lock is used to avoid concurrent modifications of data structures
    // that hold the session state
    // Please don't use it directly.
    // Also note that acquiring this lock inside read/write action may lead to the deadlock, never do it.
    private val dataLock = ReentrantReadWriteLock()

    private inline fun <R> writeData(crossinline action: () -> R) = dataLock.write(action)
    private inline fun <R> readData(crossinline action: () -> R) = dataLock.read(action)
    private fun <R> readDataWithReadAction(action: () -> R): R = readData {
        ReadAction.compute<R, Throwable>(action)
    }

    private val directoryCounter = AtomicInteger(0)

    private val classesDir: Path by lazy {
        Files.createTempDirectory("kotlin-scripting-jvm-jupyter-kernel")
    }

    private val deserializer = CompiledScriptsSerializer()

    private val _currentClasspath: TwoPartsList<File> by lazy {
        TwoPartsList<File>().apply {
            addInitial(initialClasspath)
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

    private val externalDependenciesProvider = ExecutedOnceBackgroundTask.create(
        3,
        this,
        ComputableWithName("Updating of Kotlin notebook dependencies", ::updateClasspathWithExternalDependencies)
    )

    private val implicitsList = KotlinImplicitReceiversList()
    private val classGetter = JupyterKotlinPluginScriptClassGetter(ScriptTemplateWithDisplayHelpers::class) {
        implicitsList
    }

    private var previousSessionId: JupyterNotebookSessionId? = null

    private val lastStableConfiguration = AtomicReference(project.baseScriptingCompilationConfiguration)

    private val scriptingSupportAfterUpdateListener = ScriptingSupportAfterUpdateEventProcessor()

    val executedCellsCount: Int get() = directoryCounter.get()

    init {
        thisLogger().assertTrue(virtualFile.file.isKotlinNotebook) { "$virtualFile is not a Kotlin Jupyter notebook" }
        Disposer.register(parent, this)

        project.messageBus.connect(parent).subscribe(
            SCRIPTING_SUPPORT_TOPIC,
            scriptingSupportAfterUpdateListener
        )

        externalDependenciesProvider.startIfNotStarted()
        // We need to ensure we have all dependencies before the test started
        if (ApplicationManager.getApplication().isUnitTestMode) {
            externalDependenciesProvider.join()
        }
    }

    fun scripts(): List<Pair<VirtualFile, ScriptCompilationConfigurationWrapper>> {
        return readDataWithReadAction {
            val notebookPsiFile = virtualFile.file.toPsiFile(project)
            val ktFiles = notebookPsiFile.getInjectedKtFiles()
            val configurations = ktFiles.mapNotNull { ktFile ->
                val conf = JupyterKtScriptingSupport.getConfiguration(ktFile)?.valueOrNull()
                if (conf == null || conf.dependenciesClassPath.isEmpty()) {
                    ktFile.reportAsAttachment()
                    null
                } else {
                    ktFile.virtualFile to conf
                }
            }

            configurations
        }
    }

    private fun KtFile.reportAsAttachment() {
        LOG.errorUnderDebug(
            "Empty script dependencies found",
            Attachment(
                virtualFilePath,
                text.takeIf { it.isNotEmpty() } ?: "[Injected file has no text]"
            )
        )
    }

    private suspend fun getSession(): JupyterNotebookSession? {
        return try {
            if (!ApplicationManager.getApplication().isUnitTestMode) {
                JupyterRuntimeService.getInstance(project).getOrCreateSession(virtualFile)
            } else null
        } catch (e: Throwable) {
            // TODO: show error for user with asking for configuring Python interpreter for the module
            if (e is ProcessCanceledException) throw e
            LOG.warn("Cannot create Jupyter session for Kotlin notebook", e)
            null
        }
    }

    private fun requestScriptingUpdateTestAware() {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            coroutineScope.async {
                JupyterCompilerService.getInstance(project).requestScriptingUpdate()
            }
        }
    }

    @RequiresBackgroundThread
    private fun updateClasspathWithExternalDependencies() {
        ThreadingAssertions.assertBackgroundThread()

        runBlockingCancellable {
            if (anyOf(
                ::updateClasspathWithKernelJars,
                ::updateClasspathWithProjectArtifactsAsync,
            )) {
                scriptsChangePublisher.scriptsClassesChanged(virtualFile)
                requestScriptingUpdateTestAware()
            }
        }
    }

    private suspend fun updateClasspathWithKernelJars(
        version: String = getSelectedKernelVersion(project)
    ): Boolean {
        val mavenArtifactsDownloader = KotlinNotebookMavenArtifactsDownloader.getInstance(project)
        val jars = mavenArtifactsDownloader.downloadArtifactAsync(
            KotlinNotebookMavenArtifacts.IDE_CLASSPATH_SHADOWED,
            version = version
        )
        val sourcesJars = mavenArtifactsDownloader.downloadArtifactAsync(
            KotlinNotebookMavenArtifacts.SCRIPT_CLASSPATH_SHADOWED_SOURCES,
            version = version
        )

        if (jars.isEmpty()) {
            LOG.warn("Couldn't download jars for the kernel version: $version")
        }

        writeData {
            _currentClasspath.addInitial(jars)
            _sourceRoots.addInitial(sourcesJars)
        }

        KotlinNotebookPermanentIndexService.getInstance(project)
                .addToPermanentIndex(jars.map { it.absolutePath }, sourcesJars.map { it.absolutePath })

        return jars.isNotEmpty() || sourcesJars.isNotEmpty()
    }

    private suspend fun updateClasspathWithProjectArtifactsAsync(): Boolean {
        val buildService = JupyterKotlinProjectArtifactsService.getInstance(project)
        val artifacts = buildService.buildProjectAndGetLibraries(virtualFile).ifEmpty { return false }
        return writeData {
            val oldSize = _currentClasspath.size
            _currentClasspath.addSnippet(artifacts.map { File(it) })
            val newSize = _currentClasspath.size
            oldSize != newSize
        }
    }

    fun handleBeforeCompiling(
        config: ScriptCompilationConfiguration,
        sourceCode: SourceCode? = null
    ): ScriptCompilationConfiguration {
        val sourceText = sourceCode?.text
        LOG.debug("Before-compiling callback for script: $sourceText")

        return readData {
            val withNewClasspath = config.withUpdatedClasspath(currentClasspath)
            ScriptCompilationConfiguration(withNewClasspath) {
                if (_currentClasspath.hasInitialPart) {
                    addBaseClass<ScriptTemplateWithDisplayHelpers>()
                }

                hostConfiguration.update {
                    it.with {
                        getScriptingClass(classGetter)
                    }
                }
                implicitReceivers(implicitsList)
                defaultImports(additionalDefaultImports.getList())
                ide.dependenciesSources(
                    JvmDependency(
                        project.allSourceRoots() + _sourceRoots.getList()
                    )
                )
            }
        }
    }

    @RequiresEdt
    fun addCompiledSnippet(
        snippetMetadata: EvaluatedSnippetMetadata,
        psiCell: JupyterPsiCell?,
        updateAction: () -> Unit
    ) {
        KotlinNotebookPluginUpdater.getInstance().pluginUsed()
        // execute not on EDT
        coroutineScope.async {
            try {
                val sessionId = getSession()?.sessionId
                writeData {
                    addNewDependencies(sessionId, snippetMetadata, psiCell)
                }
                updateAction()
            } catch (e: Exception) {
                if (e is ProcessCanceledException) {
                    throw e
                }
                LOG.error(e)
            }
        }
    }

    private fun getLineFolderName(lineNumber: Int) = "line_$lineNumber"

    private fun addNewDependencies(
        sessionId: JupyterNotebookSessionId?,
        snippetMetadata: EvaluatedSnippetMetadata,
        psiCell: JupyterPsiCell?
    ) {
        if (sessionId != previousSessionId) {
            LOG.info("Clearing Kotlin snippets. Previous session ID: ${previousSessionId?.id}")
            clearPreviousSnippets()
            previousSessionId = sessionId
        }

        // TODO: compare text in snippet metadata with cell source and add a source file to directory and to the container
        val nextCounter = directoryCounter.incrementAndGet()

        val lineClassesDir = classesDir.resolve(getLineFolderName(nextCounter))
        val lineClassesDirAsFile = lineClassesDir.toFile()
        lineClassesDirAsFile.mkdirs()

        val lineSourcesDir = classesDir.resolve("sources_$nextCounter")

        KotlinNotebookPermanentIndexService.getInstance(project).addToPermanentIndex(snippetMetadata.newClasspath, snippetMetadata.newSources)
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

        if (psiCell != null) {
            coroutineScope.async {
                smartReadAction(project) {
                    NotebookStructureTrackerService.getForFile(project, virtualFile)
                        .storeCompliedDataInCell(snippetMetadata, psiCell)
                }
            }
        }

        val kClassNames = deserializer.deserializeAndSave(snippetMetadata.compiledData, lineClassesDir, lineSourcesDir)
        loadReceiverClassesIfAny(lineClassesDir, kClassNames)
        //LOG.warn("Added new classes to load: $kClassNames")
    }

    fun provideDefaultConfiguration(sourceCode: SourceCode): ScriptCompilationConfigurationResult {
        requestScriptingUpdateTestAware()

        return ScriptCompilationConfigurationWrapper.FromCompilationConfiguration(
            sourceCode,
            lastStableConfiguration.get()
        ).asSuccess()
    }

    private fun createNextClassLoader(classesDirPath: Path): ClassLoader = URLClassLoader(
        arrayOf(classesDirPath.toUri().toURL()),
        (implicitsList.lastOrNull()?.fromClass ?: this::class).java.classLoader
    )

    // Returns true if some receiver classes were loaded, false otherwise
    private fun loadReceiverClassesIfAny(classesDirPath: Path, classesToLoad: Collection<String>): Boolean {
        if (classesToLoad.isEmpty()) {
            return false
        }

        return runSafelyTyped(
            action = {
                writeData {
                    val loader = createNextClassLoader(classesDirPath)
                    classesToLoad.forEach { className ->
                        LOG.debug("Adding class: $className")
                        val kClass = loader.loadClass(className).kotlin
                        implicitsList.addClass(kClass)
                    }
                    true
                }
            },
            onFailure = { e ->
                when (e) {
                    is UnsupportedClassVersionError -> {
                        val msg = e.message?.substringAfter("has been compiled by a more recent version of the Java Runtime") ?: ""
                        NotebookNotificationUtility.kernelRelatedFactory.showKernelJDKInconsistentError(project, msg)
                        true
                    }
                    else -> {
                        LOG.error(e)
                        false
                    }
                }
            }
        )
    }

    private fun clearPreviousSnippets() {
        _currentClasspath.clear()
        additionalDefaultImports.clear()
        implicitsList.clear()
        lastStableConfiguration.set(project.baseScriptingCompilationConfiguration)
        if (!project.isDisposed) {
            NotebookStructureTrackerService.getForFile(project, virtualFile).notebookDataCleared()
        }
    }

    fun clear() {
        writeData {
            clearPreviousSnippets()

            classesDir.delete(true)
            coroutineScope.cancel()
        }

        ClasspathToVfsConverter.clearCaches()

        if (!isDisposed) {
            val manager = ScriptConfigurationManager.getInstance(project) as? CompositeScriptConfigurationManager
            manager?.updater?.invalidateAndCommit()
        }
    }

    override fun dispose() {
        isDisposed = true
        clear()
    }

    private inner class ScriptingSupportAfterUpdateEventProcessor : ScriptingSupportAfterUpdateListener {
        private fun updateLastKnownConfiguration() {
            while (true) {
                val lastStableConf = lastStableConfiguration.get()
                val updatedConfiguration = handleBeforeCompiling(project.baseScriptingCompilationConfiguration)

                if (lastStableConfiguration.compareAndSet(lastStableConf, updatedConfiguration)) {
                    LOG.info("Cached configuration updated for ${virtualFile.file.name}!")
                    break
                }
            }
        }

        private fun checkLastDependenciesPresentInCache(): Boolean {
            val cache = project.scriptConfigurationsClassCache

            val lastCompiledSnippetPath = classesDir
                .resolve(
                    getLineFolderName(directoryCounter.get())
                ).toString()
            return cache.allDependenciesClassFiles.any { it.presentableUrl == lastCompiledSnippetPath }
        }

        override fun afterUpdate() {
            coroutineScope.async {
                if (previousSessionId == null) return@async

                if (!checkLastDependenciesPresentInCache()) {
                    return@async
                }

                updateLastKnownConfiguration()
                scriptsChangePublisher.scriptsClassesChanged(virtualFile)
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(JupyterCompilerPerFileService::class.java)
    }
}
