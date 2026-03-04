// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.jupyter.cells.ExecutedCellData
import com.intellij.kotlin.jupyter.core.logging.KotlinNotebookLoggerFactory
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.notifications.notebookNotifications
import com.intellij.kotlin.jupyter.core.projectModel.JupyterKotlinProjectArtifactsService
import com.intellij.kotlin.jupyter.core.projectModel.JupyterKotlinProjectArtifactsService.Companion.buildProjectAndGetLibraries
import com.intellij.kotlin.jupyter.core.projectModel.KotlinNotebookPermanentIndexService
import com.intellij.kotlin.jupyter.core.projectModel.showKernelAndModuleJdkAreMatchingWarningIfNeeded
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifacts
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifactsDownloader
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.NotebookScriptsStateListener.UpdateState
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.SCRIPTING_SUPPORT_TOPIC
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.ScriptingSupportUpdateEventsListener
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.actions.promptSessionShutdownIfNeeded
import com.intellij.kotlin.jupyter.core.settings.selectedKernelVersionAsString
import com.intellij.kotlin.jupyter.core.util.ComputableWithName
import com.intellij.kotlin.jupyter.core.util.ExecutedOnceBackgroundTask
import com.intellij.kotlin.jupyter.core.util.NotebookPerFileChildService
import com.intellij.kotlin.jupyter.core.util.debugWithAttachments
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.core.util.getInjectedKtFiles
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.onAnyOf
import com.intellij.kotlin.jupyter.core.util.runSafely
import com.intellij.kotlin.jupyter.core.util.sourceRootsForDependencies
import com.intellij.kotlin.jupyter.core.util.withReadAccess
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.delete
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.config.addBaseClass
import org.jetbrains.kotlinx.jupyter.config.defaultGlobalImports
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString
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
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm
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
 */
class JupyterCompilerPerFileService(
    private val projectService: JupyterCompilerService,
    virtualFile: BackedNotebookVirtualFile,
    initialClasspath: List<Path>,
    scope: CoroutineScope
) : NotebookPerFileChildService(virtualFile, scope) {
    private val project get() = projectService.project

    private val scriptsChangePublisher: NotebookScriptsStateListener? get() {
        return project.messageBus
            .takeIf { !it.isDisposed }
            ?.syncPublisher(NotebookScriptsStateListener.TOPIC)
    }

    init {
        Disposer.register(projectService, this)
    }

    private val _updateState = MutableStateFlow(UpdateState.NEEDS_UPDATE)

    // This lock is used to avoid concurrent modifications of data structures
    // that hold the session state from coroutines.
    // Please don't use it directly.
    // Also note that acquiring this lock inside a read / write action may lead to the deadlock, never do it.
    // Use 'accessDataBlocking' for non-suspended context.
    private val dataLock = Mutex()
    private suspend inline fun <R> accessData(crossinline action: () -> R) = dataLock.withLock(null, action)
    private fun <R> accessDataBlocking(action: () -> R): R = runBlockingMaybeCancellable {
        accessData {
            action()
        }
    }

    private val directoryCounter = AtomicInteger(0)
    private val lastClasspathUpdate = AtomicReference<Path>()

    private val classesDir: Path by lazy {
        Files.createTempDirectory("kotlin-scripting-jvm-jupyter-kernel")
    }

    private val deserializer = CompiledScriptsSerializer()

    private val _currentClasspath: TwoPartsList<Path> by lazy {
        TwoPartsList<Path>().apply {
            addInitial(initialClasspath)
        }
    }
    val currentClasspath: List<Path> get() = _currentClasspath.getList()

    private val _sourceRoots = TwoPartsList<Path>()
    val currentSourceRoots: List<Path> get() = _sourceRoots.getList()

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

    private val lastStableConfiguration = AtomicReference(project.baseScriptingCompilationConfiguration)

    private val scriptingSupportUpdatesProcessor = ScriptingSupportEventsProcessor()

    val executedCellsCount: Int get() = directoryCounter.get()

    /**
     * To determine whatever this [service] has
     * pending update to Scripting infrastructure.
     */
    val needsConfigurationUpdate: Boolean get() {
        return when (_updateState.value) {
            UpdateState.NEEDS_UPDATE, UpdateState.PENDING -> true
            UpdateState.SKIPPED, UpdateState.COMPLETE -> {
                val hasNewReceivers = scriptingSupportUpdatesProcessor.lastLoadedTypeOrNull != null

                when {
                    hasNewReceivers -> true
                    // means service is restarted; the base class is absent
                    lastStableConfiguration.get() == project.baseScriptingCompilationConfiguration -> true
                    else -> false
                }
            }
        }
    }

    init {
        notebookLogger().assertTrue(virtualFile.file.isKotlinNotebook) { "$virtualFile is not a Kotlin Jupyter notebook" }
        project.messageBus.connect(this).subscribe(
            SCRIPTING_SUPPORT_TOPIC,
            scriptingSupportUpdatesProcessor
        )
        KotlinNotebookProjectOptionsProvider.getInstance(project).addListener(
            object : KotlinNotebookProjectOptionsProvider.Listener {
                override fun onJdkChanged() {
                    checkIfSessionRestartIsNeeded()
                }
            }, this)

        externalDependenciesProvider.startIfNotStarted()
        // We need to ensure we have all dependencies before the test started
        if (ApplicationManager.getApplication().isUnitTestMode) {
            externalDependenciesProvider.join()
        }
    }

    fun scripts(): List<Pair<VirtualFile, ScriptCompilationConfigurationWrapper>> {
        val ktFiles = ReadAction.compute<List<KtFile>, Throwable> {
            virtualFile.getInjectedKtFiles(project)
        }.ifEmpty {
            return emptyList()
        }
        val currentConfiguration = ktFiles.getSampleConfiguration() ?: return emptyList()

        return ktFiles.map { file ->
            file.virtualFile to currentConfiguration.second
        }
    }

    suspend fun scriptsAsync(): List<Pair<VirtualFile, ScriptCompilationConfigurationWrapper>> {
        val ktFiles = readAction {
            virtualFile.getInjectedKtFiles(project)
        }
        val currentConfiguration = ktFiles.getSampleConfiguration() ?: return emptyList()

        return ktFiles.map { file ->
            file.virtualFile to currentConfiguration.second
        }
    }

    fun getFilesToRefine(): List<KtFileScriptSource> {
        val notebookPsiFile = virtualFile.file.findPsiFile(project)
        return notebookPsiFile.getInjectedKtFiles().map { KtFileScriptSource(it) }
    }

    private fun checkIfSessionRestartIsNeeded() {
        promptSessionShutdownIfNeeded(project, virtualFile) {
            val notebook = virtualFile.notebookOrNull
            notebook?.showKernelAndModuleJdkAreMatchingWarningIfNeeded(project)
        }
    }

    private fun Collection<KtFile>.getSampleConfiguration(): Pair<VirtualFile, ScriptCompilationConfigurationWrapper>? {
        return firstNotNullOfOrNull { ktFile -> getConfiguration(ktFile)?.let { ktFile.virtualFile to it }  }
    }

    private fun requestScriptingUpdateTestAware() {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            projectService.requestScriptingUpdate()
            _updateState.value = UpdateState.PENDING
        }
    }

    @RequiresBackgroundThread
    private fun updateClasspathWithExternalDependencies() {
        ThreadingAssertions.assertBackgroundThread()

        runBlockingCancellable {
            onAnyOf(
                ::updateClasspathWithKernelJars,
                ::updateClasspathWithProjectArtifactsAsync,
            ) {
                requestScriptingUpdateTestAware()
            }
        }
    }

    private fun Collection<String>.updateLastClasspathArtifact() {
        val lastClasspathUpdateValue = lastOrNull()
        val asPath = lastClasspathUpdateValue?.toNioPathOrNull()
        if (asPath != null) {
            lastClasspathUpdate.set(asPath)
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
            KotlinNotebookMavenArtifacts.IDE_CLASSPATH_SHADOWED_SOURCES,
            version = version
        )

        if (jars.isEmpty()) {
            LOG.warn("Couldn't download jars for the kernel version: $version")
        }

        accessData {
            _currentClasspath.addInitial(jars)
            _sourceRoots.addInitial(sourcesJars)
        }

        val kernelArtifactPaths = jars.map { it.absolutePathString() }
        KotlinNotebookPermanentIndexService.getInstance(project)
            .addToPermanentIndex(kernelArtifactPaths, sourcesJars.map { it.absolutePathString() })
        kernelArtifactPaths.updateLastClasspathArtifact()

        return jars.isNotEmpty() || sourcesJars.isNotEmpty()
    }

    private suspend fun updateClasspathWithProjectArtifactsAsync(): Boolean {
        val buildService = JupyterKotlinProjectArtifactsService.getInstance(project)
        val artifacts = buildService.buildProjectAndGetLibraries(virtualFile).ifEmpty { return false }
        return accessData {
            val oldSize = _currentClasspath.size
            _currentClasspath.addSnippet(artifacts.map { Path.of(it) })
            val newSize = _currentClasspath.size
            oldSize != newSize
        }
    }

    // NB: This method should be called once per notebook configuration setup,
    // as all dependencies are the same between all cells.
    fun handleBeforeCompiling(
        config: ScriptCompilationConfiguration,
        sourceCode: SourceCode? = null
    ): ScriptCompilationConfiguration {
        // prefer fine-grained locks
        return withReadAccess {
            accessDataBlocking {
                val sourceText = sourceCode?.text
                LOG.debug("Before-compiling callback for script: $sourceText")

                config.refineConfiguration()
            }
        }
    }

    suspend fun handleBeforeCompilingAsync(
        config: ScriptCompilationConfiguration,
    ) : ScriptCompilationConfiguration {
        LOG.debug("Before-compiling callback ")

        return accessData {
            config.refineConfiguration()
        }
    }

    private fun ScriptCompilationConfiguration.refineConfiguration(): ScriptCompilationConfiguration {
        val withNewClasspath = withUpdatedClasspath(currentClasspath.map { it.toFile() })
        return ScriptCompilationConfiguration(withNewClasspath) {
            if (_currentClasspath.hasInitialPart) {
                // `addBaseClas` is the wrong name but is only used for backwards compatibility.
                // Should be renamed once K2 Support is stable.
                addBaseClass<ScriptTemplateWithDisplayHelpers>()
            }

            hostConfiguration.update {
                it.with {
                    getScriptingClass(classGetter)
                }
            }
            /**
             * We do need to create a copy here,
             * otherwise all changes made to this list will eventually appear in the cache without an update,
             * and no consistency checks can be done.
             */
            val updatedReceivers = implicitsList.toList() + KotlinType(ScriptTemplateWithDisplayHelpers::class)
            implicitReceivers(updatedReceivers)
            defaultImports(additionalDefaultImports.getList())
            ide.dependenciesSources(
                JvmDependency(
                    (project.sourceRootsForDependencies(virtualFile) + _sourceRoots.getList().toSet())
                        .map { it.toFile() }
                )
            )
        }
    }

    fun addCompiledSnippet(
        snippetMetadata: EvaluatedSnippetMetadata,
        psiCell: JupyterPsiCell?,
        cellIndex: Int = ABSENT_CELL_INDEX
    ) {
        coroutineScope.async {
            try {
                val executedCellData = ExecutedCellData(
                    cellIndex = cellIndex,
                    psiCell = psiCell,
                    notebookCell = virtualFile.notebookOrNull?.getCellOrNull(cellIndex),
                )
                val receiversDeferred = accessData {
                    addNewDependencies(snippetMetadata, executedCellData)
                }
                // Await to ensure receivers are available before the update cycle
                receiversDeferred?.await()

                requestScriptingUpdate()
            } catch (e: Exception) {
                if (e is ProcessCanceledException) {
                    throw e
                }
                LOG.error(e)
            }
        }
    }

    fun requestScriptingUpdate() {
        projectService.requestScriptingUpdate()
        _updateState.value = UpdateState.PENDING
    }

    private fun getLineFolderName(lineNumber: Int) = "line_$lineNumber"

    private fun getLastScriptArtifactPath(): Path? {
        return lastClasspathUpdate.get()
    }

    /**
     * Adds new dependencies from the executed snippet.
     *
     * Returns a [Deferred] that completes when receiver classes are loaded,
     * or `null` if there are no receiver classes to load.
     */
    private fun addNewDependencies(
        snippetMetadata: EvaluatedSnippetMetadata,
        executedCellData: ExecutedCellData
    ): Deferred<Unit>? {
        // TODO: compare text in snippet metadata with cell source and add a source file to directory and to the container
        val nextCounter = directoryCounter.incrementAndGet()

        val lineClassesDir = classesDir.resolve(getLineFolderName(nextCounter))
        Files.createDirectories(lineClassesDir)

        val lineSourcesDir = classesDir.resolve("sources_$nextCounter")

        KotlinNotebookPermanentIndexService.getInstance(project).addToPermanentIndex(snippetMetadata.newClasspath, snippetMetadata.newSources)
        _currentClasspath.addSnippetFromData(snippetMetadata.newClasspath.map { Path.of(it) }, lineClassesDir)
        _sourceRoots.addSnippetFromData(snippetMetadata.newSources.map { Path.of(it) }, lineSourcesDir)
        additionalDefaultImports.addSnippet(snippetMetadata.newImports)
        if (snippetMetadata.newClasspath.isEmpty()) {
            lastClasspathUpdate.set(lineClassesDir)
        } else {
            snippetMetadata.newClasspath.updateLastClasspathArtifact()
        }

        val psiCell = executedCellData.psiCell
        coroutineScope.async {
            if (psiCell != null) {
                smartReadAction(project) {
                    NotebookStructureTrackerService.getForFile(project, virtualFile)
                        .storeCompliedDataInCell(snippetMetadata, executedCellData)
                }
            }
        }

        val compiledClassifiers = snippetMetadata.compiledData.scripts.filterNot { it.isImplicitReceiver }
        val kClassNames = deserializer.deserializeAndSave(snippetMetadata.compiledData, lineClassesDir, lineSourcesDir)

        if (kClassNames.isEmpty()) return null

        return coroutineScope.async {
            if (loadReceiverClassesIfAny(lineClassesDir, kClassNames)) {
                defaultImportsEnhancer.updateDefaultImports(
                    compiledClassifiers, additionalDefaultImports
                )
            }
        }
    }

    fun provideDefaultConfiguration(sourceCode: SourceCode): ScriptCompilationConfigurationResult {
        return ScriptCompilationConfigurationWrapper(
            sourceCode,
            lastStableConfiguration.get()
        ).with {
            getSelectedSdkOrAnyAcceptable(project)?.homePath?.let {
                jvm.jdkHome(File(it))
            }
        }.asSuccess()
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
    private suspend fun loadReceiverClassesIfAny(classesDirPath: Path, classesToLoad: Collection<String>): Boolean {
        if (classesToLoad.isEmpty()) {
            return false
        }

        return runSafely(
            action = {
                accessData {
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
                        return true
                    }
                    else -> {
                        LOG.error(e)
                    }
                }
            }
        ) == true
    }

    override fun dispose() {
        coroutineScope.async {
            accessData {
                _currentClasspath.clear()
                additionalDefaultImports.clear()
                implicitsList.clear()
                scriptingSupportUpdatesProcessor.clear()
                lastStableConfiguration.set(project.baseScriptingCompilationConfiguration)
                defaultImportsEnhancer.clear()
                if (!project.isDisposed) {
                    NotebookStructureTrackerService.getInstance(project).remove(virtualFile)
                }
                classesDir.delete(true)
            }
            coroutineScope.cancel()
        }
    }

    private inner class ScriptingSupportEventsProcessor : ScriptingSupportUpdateEventsListener, ImplicitListsConfigurationUpdater {
        private val implicitReceiversClassPathData = ConcurrentLinkedQueue<ClassPathSnippetsLoadedData>()
        private val implicitListsUpdateMutex = Mutex()

        val lastLoadedTypeOrNull: KotlinType? get() {
            val loadedSnippets = implicitReceiversClassPathData.lastOrNull()?.snippetTypes
            return loadedSnippets?.lastOrNull()
        }

        override suspend fun getSnippetsReadyForConfigurationUpdate(): List<ClassPathSnippetsLoadedData> {
            return implicitReceiversClassPathData.toList().filter {
                val presentTypes = scriptConsistencyVerifier.filterTypesPresentInIndexes(virtualFile, it.snippetTypes)
                presentTypes == it.snippetTypes
            }
        }

        override fun addLoadedSnippet(snippetData: ClassPathSnippetsLoadedData) {
            implicitReceiversClassPathData.add(snippetData)
        }

        override fun afterUpdate(notebooks: Collection<BackedNotebookVirtualFile>?) {
            coroutineScope.async {
                afterUpdateImpl(notebooks)
            }
        }

        override fun onUpdateException(exception: Throwable) {
            LOG.warn("Exception during scripting update: ${exception.message}")
        }

        fun clear() {
            implicitReceiversClassPathData.clear()
        }

        private suspend fun afterUpdateImpl(notebooks: Collection<BackedNotebookVirtualFile>?) {
            val updateState = processUpdate(notebooks)
            _updateState.value = updateState

            readAction {
                scriptsChangePublisher?.scriptsConfigurationUpdated(virtualFile, updateState)
                if (updateState == UpdateState.COMPLETE) {
                    virtualFile.file.findPsiFile(project)?.let { psiFile ->
                        DaemonCodeAnalyzer.getInstance(project).restart(psiFile, this)
                    }
                }
            }
        }

        private suspend fun processUpdate(notebooks: Collection<BackedNotebookVirtualFile>?): UpdateState {
            val lastScriptPath = getLastScriptArtifactPath()
            if (!shouldProcessUpdate(lastScriptPath, notebooks)) {
                return UpdateState.SKIPPED
            }

            val mightBeComplete = checkIfUpdatePotentiallyCompleted(lastScriptPath!!)
            if (!mightBeComplete) {
                return UpdateState.NEEDS_UPDATE
            }

            updateLastStableConfiguration()

            return implicitListsUpdateMutex.withLock {
                updateImplicitLists()
            }
        }

        private fun checkIfUpdatePotentiallyCompleted(lastScriptPath: Path): Boolean {
            val vFileUrl = lastScriptPath.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())

            return when {
                !scriptConsistencyVerifier.isScriptPathConsistentWithModel(virtualFile, vFileUrl) -> {
                    LOG.info("Configuration is not consistent for ${virtualFile.file.name}, absent $lastScriptPath, fileUrl: ${vFileUrl.url}")
                    false
                }
                // might be potentially complete, further checks needed
                else -> true
            }
        }

        private fun shouldProcessUpdate(lastScriptPath: Path?, notebooks: Collection<BackedNotebookVirtualFile>?): Boolean {
            return when {
                lastScriptPath == null -> {
                    LOG.debug("Configuration for ${virtualFile.file.name} is not updated, no last script path")
                    false
                }
                notebooks != null && virtualFile !in notebooks -> {
                    LOG.debug("Configuration for ${virtualFile.file.name} is not updated, it's not in the list of updated notebooks ($notebooks)")
                    false
                }
                else -> true
            }
        }

        private suspend fun updateLastStableConfiguration() {
            while (true) {
                val lastStableConf = lastStableConfiguration.get()
                val updatedConfiguration = handleBeforeCompilingAsync(project.baseScriptingCompilationConfiguration)

                if (lastStableConfiguration.compareAndSet(lastStableConf, updatedConfiguration)) {
                    LOG.info("Cached configuration updated for ${virtualFile.file.name}!")
                    break
                }
            }
        }

        /**
         * It might be the case that added new classes are not yet present in stored configurations.
         * For them to appear in the stable configuration cache, we need to invoke update once again.
         */
        private suspend fun updateImplicitLists(): UpdateState {
            if (implicitReceiversClassPathData.isEmpty()) {
                // nothing to update, one needs to check the state
                return if (checkConfigurationNeedsUpdate()) {
                    UpdateState.NEEDS_UPDATE
                } else {
                    UpdateState.COMPLETE
                }
            }

            val newStableReceivers = getSnippetsReadyForConfigurationUpdate()

            accessData {
                newStableReceivers.flatMap { it.snippetTypes }.forEach {
                    implicitsList.addClass(it.fromClass!!)
                }
            }

            LOG.debug {
                "Added classes in ${virtualFile.file.name} to implicitList: ${newStableReceivers.flatMap { it.snippetTypes.map { type -> type.typeName } }}"
            }

            // someone already made everything
            if (implicitReceiversClassPathData.isEmpty()) {
                return UpdateState.COMPLETE
            }

            implicitReceiversClassPathData.removeAll(newStableReceivers.toSet())

            requestScriptingUpdate()
            return UpdateState.PENDING
        }

        private suspend fun checkConfigurationNeedsUpdate(): Boolean {
            return !scriptConsistencyVerifier.isScriptFileConfigurationConsistentWithModel(
                virtualFile,
                handleBeforeCompilingAsync(project.baseScriptingCompilationConfiguration)
            )
        }
    }

    companion object {
        private val LOG = KotlinNotebookLoggerFactory.getInstance(JupyterCompilerPerFileService::class)

        /**
         * Default value for a cell for which we can't determine the index,
         * e.g., if some session code was executed.
         */
        private const val ABSENT_CELL_INDEX: Int = 1

        fun getConfiguration(ktFile: KtFile): ScriptCompilationConfigurationWrapper? {
            // should we prefer getting our own definition directly?
            val scriptDef = ktFile.findScriptDefinition() ?: return null
            val conf = refineScriptCompilationConfiguration(KtFileScriptSource(ktFile), scriptDef, ktFile.project).valueOrNull()
            if (conf == null || conf.dependenciesClassPath.isEmpty()) {
                ktFile.reportAsAttachment()
            }

            return conf
        }

        private fun KtFile.reportAsAttachment() {
            LOG.debugWithAttachments(
                message = { "Empty script dependencies found" },
                attachments = {
                    listOf(
                        Attachment(
                            virtualFilePath,
                            text.takeIf { it.isNotEmpty() } ?: "[Injected file has no text]"
                        )
                    )
                }
            )
        }
    }
}