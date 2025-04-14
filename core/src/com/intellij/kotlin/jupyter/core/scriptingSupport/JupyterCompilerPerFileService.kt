// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSessionId
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterRuntimeService
import com.intellij.kotlin.jupyter.core.debug.variables.KotlinNotebookSessionVariablesService
import com.intellij.kotlin.jupyter.core.logging.KotlinNotebookLoggerFactory
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.notifications.notebookNotifications
import com.intellij.kotlin.jupyter.core.projectModel.JupyterKotlinProjectArtifactsService
import com.intellij.kotlin.jupyter.core.projectModel.JupyterKotlinProjectArtifactsService.Companion.buildProjectAndGetLibraries
import com.intellij.kotlin.jupyter.core.projectModel.KotlinNotebookPermanentIndexService
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifacts
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifactsDownloader
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.SCRIPTING_SUPPORT_TOPIC
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.ScriptingSupportUpdateEventsListener
import com.intellij.kotlin.jupyter.core.settings.selectedKernelVersionAsString
import com.intellij.kotlin.jupyter.core.util.ComputableWithName
import com.intellij.kotlin.jupyter.core.util.ExecutedOnceBackgroundTask
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.kotlin.jupyter.core.util.anyOf
import com.intellij.kotlin.jupyter.core.util.errorUnderDebug
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.core.util.getInjectedKtFiles
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.runSafelyTyped
import com.intellij.kotlin.jupyter.core.util.sourceRootsForDependencies
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.io.delete
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.config.addBaseClass
import org.jetbrains.kotlinx.jupyter.config.defaultGlobalImports
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.script.experimental.api.KotlinType
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
 * and provides scripting support for injected Kotlin snippets
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
        project.messageBus.syncPublisher(NotebookScriptsStateListener.TOPIC)

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
    private val lastClasspathUpdate = AtomicReference<String>()

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
    private val defaultImportsEnhancer = CompiledClassifiersDefaultImportsEnhancer.create(project, this)

    private val externalDependenciesProvider = ExecutedOnceBackgroundTask.create(
        3,
        this,
        ComputableWithName("Updating of Kotlin notebook dependencies", ::updateClasspathWithExternalDependencies)
    )
    private val scriptConsistencyVerifier = ScriptingEntitiesConsistencyVerifier.create(project)

    private val implicitsList = KotlinImplicitReceiversList()
    private val classGetter = JupyterKotlinPluginScriptClassGetter(ScriptTemplateWithDisplayHelpers::class) {
        implicitsList
    }

    private var previousSessionId: JupyterNotebookSessionId? = null

    private val lastStableConfiguration = AtomicReference(project.baseScriptingCompilationConfiguration)

    private val scriptingSupportUpdatesProcessor = ScriptingSupportEventsProcessor()

    val stableConfiguration: ScriptCompilationConfiguration get() = lastStableConfiguration.get()
    val executedCellsCount: Int get() = directoryCounter.get()

    /**
     * To determine whatever this [service] has
     * pending update to Scripting infrastructure.
     */
    val needsConfigurationUpdate: Boolean get() {
        val hasNewReceivers = readData {
            scriptingSupportUpdatesProcessor.lastLoadedTypeOrNull != null
        }
        if (hasNewReceivers) {
            return true
        }

        return !scriptConsistencyVerifier.isScriptFileConfigurationConsistentWithModel(
            virtualFile,
            handleBeforeCompiling(project.baseScriptingCompilationConfiguration)
        )
    }

    init {
        notebookLogger().assertTrue(virtualFile.file.isKotlinNotebook) { "$virtualFile is not a Kotlin Jupyter notebook" }
        Disposer.register(parent, this)

        project.messageBus.connect(parent).subscribe(
            SCRIPTING_SUPPORT_TOPIC,
            scriptingSupportUpdatesProcessor
        )

        externalDependenciesProvider.startIfNotStarted()
        // We need to ensure we have all dependencies before the test started
        if (ApplicationManager.getApplication().isUnitTestMode) {
            externalDependenciesProvider.join()
        }
    }

    fun scripts(): List<Pair<VirtualFile, ScriptCompilationConfigurationWrapper>> {
        return readDataWithReadAction {
            val notebookPsiFile = virtualFile.file.findPsiFile(project)
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

    fun getFilesToRefine(): List<KtFileScriptSource> {
        val notebookPsiFile = virtualFile.file.findPsiFile(project)
        return notebookPsiFile.getInjectedKtFiles().map { KtFileScriptSource(it) }
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
            JupyterCompilerService.getInstance(project).requestScriptingUpdate()
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
                requestScriptingUpdateTestAware()
            }
        }
    }

    private fun Collection<String>.updateLastClasspathArtifact() {
        val lastClasspathUpdateValue = lastOrNull()
        if (lastClasspathUpdateValue != null) {
            lastClasspathUpdate.set(lastClasspathUpdateValue)
        }
    }

    private suspend fun updateClasspathWithKernelJars(
        version: String = project.selectedKernelVersionAsString
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

        val kernelArtifactPaths = jars.map { it.absolutePath }
        KotlinNotebookPermanentIndexService.getInstance(project)
                .addToPermanentIndex(kernelArtifactPaths, sourcesJars.map { it.absolutePath })
        kernelArtifactPaths.updateLastClasspathArtifact()

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

    @RequiresReadLock
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
                        project.sourceRootsForDependencies(virtualFile) + _sourceRoots.getList()
                    )
                )
            }
        }
    }

    fun addCompiledSnippet(
      snippetMetadata: EvaluatedSnippetMetadata,
      psiCell: JupyterPsiCell?,
    ) {
        coroutineScope.async {
            try {
                val sessionId = getSession()?.sessionId
                writeData {
                    addNewDependencies(sessionId, snippetMetadata, psiCell)
                }

                requestScriptingUpdate()
            } catch (e: Exception) {
                if (e is ProcessCanceledException) {
                    throw e
                }
                LOG.error(e)
            }
        }
    }

    private fun requestScriptingUpdate() {
        JupyterCompilerService.getInstance(project).requestScriptingUpdate()
    }

    private fun getLineFolderName(lineNumber: Int) = "line_$lineNumber"

    private fun getLastScriptArtifactPath(): String? {
        return lastClasspathUpdate.get()
    }

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
        _currentClasspath.addSnippetFromData(snippetMetadata.newClasspath.map { File(it) }, lineClassesDirAsFile)
        _sourceRoots.addSnippetFromData(snippetMetadata.newSources.map { File(it) }, lineSourcesDir.toFile())
        additionalDefaultImports.addSnippet(snippetMetadata.newImports)
        if (snippetMetadata.newClasspath.isEmpty()) {
            lastClasspathUpdate.set(lineClassesDirAsFile.absolutePath)
        } else {
            snippetMetadata.newClasspath.updateLastClasspathArtifact()
        }

        if (psiCell != null) {
            coroutineScope.async {
                smartReadAction(project) {
                    NotebookStructureTrackerService.getForFile(project, virtualFile)
                        .storeCompliedDataInCell(snippetMetadata, psiCell)
                }
                KotlinNotebookSessionVariablesService.getForFile(project, virtualFile)
                    .updateSnippetsMetaData(snippetMetadata)
            }
        }

        val compiledClassifiers = snippetMetadata.compiledData.scripts.filterNot { it.isImplicitReceiver }
        val kClassNames = deserializer.deserializeAndSave(snippetMetadata.compiledData, lineClassesDir, lineSourcesDir)

        if (loadReceiverClassesIfAny(lineClassesDir, kClassNames)) {
            defaultImportsEnhancer.updateDefaultImports(
                compiledClassifiers, additionalDefaultImports
            )
        }
    }

    fun provideDefaultConfiguration(sourceCode: SourceCode): ScriptCompilationConfigurationResult {
        requestScriptingUpdateTestAware()

        return ScriptCompilationConfigurationWrapper.FromCompilationConfiguration(
            sourceCode,
            lastStableConfiguration.get()
        ).asSuccess()
    }

    private fun <T> TwoPartsList<T>.addSnippetFromData(collection: Collection<T>, vararg elements: T) {
        val listToAdd = ArrayList<T>(collection.size + elements.size).apply {
            elements.forEach { el ->
                add(el)
            }

            collection.forEach { el ->
                add(el)
            }
        }

        addSnippet(listToAdd)
    }

    private fun createNextClassLoader(classesDirPath: Path): ClassLoader {
        val lastSaved = scriptingSupportUpdatesProcessor.lastLoadedTypeOrNull?.fromClass
        val lastLoadedClass = lastSaved ?: implicitsList.lastOrNull()?.fromClass
        return URLClassLoader(
            arrayOf(classesDirPath.toUri().toURL()),
            (lastLoadedClass ?: this::class).java.classLoader
        )
    }

    // Returns true if some receiver classes were loaded, false otherwise
    private fun loadReceiverClassesIfAny(classesDirPath: Path, classesToLoad: Collection<String>): Boolean {
        if (classesToLoad.isEmpty()) {
            return false
        }

        return runSafelyTyped(
            action = {
                writeData {
                    val loader = createNextClassLoader(classesDirPath)
                    val loadedSnippets = classesToLoad.map { className ->
                        LOG.debug("Adding class: $className")
                        loader.loadClass(className).kotlin
                    }
                    scriptingSupportUpdatesProcessor.addLoadedSnippet(
                        ClassPathSnippetsLoadedData(
                            classesDirPath,
                            loadedSnippets.map { KotlinType(it) },
                        )
                    )
                    true
                }
            },
            onFailure = { e ->
                when (e) {
                    is UnsupportedClassVersionError -> {
                        LOG.warn(e)
                        val msg = e.message?.substringAfter("has been compiled by a more recent version of the Java Runtime") ?: ""
                        project.notebookNotifications.showKernelJDKInconsistentError(msg)
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
        scriptingSupportUpdatesProcessor.clear()
        lastStableConfiguration.set(project.baseScriptingCompilationConfiguration)
        defaultImportsEnhancer.clear()
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

        if (!isDisposed) {
            val manager = ScriptConfigurationManager.getInstance(project) as? CompositeScriptConfigurationManager
            manager?.updater?.invalidateAndCommit()
        }
    }

    override fun dispose() {
        isDisposed = true
        clear()
    }

    private inner class ScriptingSupportEventsProcessor : ScriptingSupportUpdateEventsListener, ImplicitListsConfigurationUpdater {
        private val implicitReceiversClassPathData = mutableListOf<ClassPathSnippetsLoadedData>()

        private fun updateLastStableConfiguration() {
            while (true) {
                val lastStableConf = lastStableConfiguration.get()
                val updatedConfiguration = handleBeforeCompiling(project.baseScriptingCompilationConfiguration)

                if (lastStableConfiguration.compareAndSet(lastStableConf, updatedConfiguration)) {
                    LOG.info("Cached configuration updated for ${virtualFile.file.name}!")
                    writeData {
                        updateImplicitLists()
                    }
                    break
                }
            }
        }

        /**
         * It might be the case that added new classes are not yet present in stored configurations.
         * For them to appear in the stable configuration cache, we need to invoke update once again.
         */
        private fun updateImplicitLists() {
            if (implicitReceiversClassPathData.isEmpty()) return

            val newStableReceivers = getSnippetsReadyForConfigurationUpdate()
            newStableReceivers.flatMap { it.snippetTypes }.forEach {
                implicitsList.addClass(it.fromClass!!)
            }

            implicitReceiversClassPathData.removeAll(newStableReceivers)

            // release write lock fast
            coroutineScope.async {
                requestScriptingUpdate()
            }
        }

        val lastLoadedTypeOrNull: KotlinType? get() {
            val loadedSnippets = implicitReceiversClassPathData.lastOrNull()?.snippetTypes
            return loadedSnippets?.lastOrNull()
        }

        override fun getSnippetsReadyForConfigurationUpdate(): List<ClassPathSnippetsLoadedData> {
            return implicitReceiversClassPathData.filter { snippetData ->
                scriptConsistencyVerifier.isScriptPathConsistentWithModel(virtualFile, snippetData.path.toString())
            }
        }

        override fun addLoadedSnippet(snippetData: ClassPathSnippetsLoadedData) {
            implicitReceiversClassPathData.add(snippetData)
        }

        override fun afterUpdate() {
            coroutineScope.async {
                // return if afterUpdate triggerred for another service
                val lastScriptPath = getLastScriptArtifactPath() ?: return@async

                if (!scriptConsistencyVerifier.isScriptPathConsistentWithModel(virtualFile, lastScriptPath)) {
                    scriptsChangePublisher.scriptsConfigurationUpdated(virtualFile, NotebookScriptsStateListener.UpdateState.INCOMPLETE)
                    return@async
                }

                updateLastStableConfiguration()
                scriptsChangePublisher.scriptsConfigurationUpdated(virtualFile, NotebookScriptsStateListener.UpdateState.COMPLETE)

                readAction {
                    virtualFile.file.findPsiFile(project)?.let { psiFile ->
                        DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
                    }
                }
            }
        }

        fun clear() {
            implicitReceiversClassPathData.clear()
        }
    }

    companion object {
        private val LOG = KotlinNotebookLoggerFactory.getInstance(JupyterCompilerPerFileService::class)
    }
}
