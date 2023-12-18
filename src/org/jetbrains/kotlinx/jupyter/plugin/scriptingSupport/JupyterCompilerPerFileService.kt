// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.delete
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.core.script.ClasspathToVfsConverter
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.config.addBaseClass
import org.jetbrains.kotlinx.jupyter.config.defaultGlobalImports
import org.jetbrains.kotlinx.jupyter.plugin.editor.find.NotebookReferenceFinder
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.editor.notifications.NotebookNotificationUtility
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService.Companion.buildProjectAndGetLibraries
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.KotlinNotebookPermanentIndexService
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifactsDownloader
import org.jetbrains.kotlinx.jupyter.plugin.settings.getSelectedKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.statistics.usages.KotlinNotebookPluginUpdater
import org.jetbrains.kotlinx.jupyter.plugin.util.ComputableWithName
import org.jetbrains.kotlinx.jupyter.plugin.util.ExecutedOnceBackgroundTask
import org.jetbrains.kotlinx.jupyter.plugin.util.allSourceRoots
import org.jetbrains.kotlinx.jupyter.plugin.util.anyOf
import org.jetbrains.kotlinx.jupyter.plugin.util.errorUnderDebug
import org.jetbrains.kotlinx.jupyter.plugin.util.getInjectedKtFiles
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterNotebook
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
import kotlin.math.abs
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
 * including magics handling, storing dependencies and a list
 * of compiled scripts.
 *
 * @property project       Target Project instance
 * @property virtualFile   File with Kotlin notebook
 * @param initialClasspath Initial classpath to use
 * @param parent           Parent Disposable
 */
class JupyterCompilerPerFileService(
    private val project: Project,
    private val virtualFile: BackedNotebookVirtualFile,
    private val compilerPublisher: JupyterCompilerService.CodeSnippetsChangeListener,
    initialClasspath: List<File>,
    parent: Disposable
) : Disposable {
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
            val cache = (ScriptConfigurationManager.getInstance(project) as CompositeScriptConfigurationManager)
                .updater.classpathRoots

            val lastCompiledSnippetPath = classesDir
                .resolve(
                    getLineFolderName(directoryCounter.get())
                ).toString()
            return cache.allDependenciesClassFiles.any { it.presentableUrl == lastCompiledSnippetPath }
        }

        override fun afterUpdate() {
            coroutineScope.async {
                if (previousSessionId == null) return@async

                compileLock.read {
                    if (!checkLastDependenciesPresentInCache()) {
                        return@async
                    }

                    updateLastKnownConfiguration()
                }
                compilerPublisher.scriptsClassesChanged(virtualFile)
            }
        }
    }

    private var isDisposed = false

    private val compileLock = ReentrantReadWriteLock()
    private val directoryCounter = AtomicInteger(0)
    val cellOrdinalToClassName = mutableMapOf<Int, Set<String>>()

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

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var previousSessionId: JupyterNotebookSessionId? = null

    private val lastStableConfiguration = AtomicReference(project.baseScriptingCompilationConfiguration)

    private val scriptingSupportAfterUpdateListener = ScriptingSupportAfterUpdateEventProcessor()

    val executedCellsCount: Int get() = directoryCounter.get()

    fun scripts(): List<Pair<VirtualFile, ScriptCompilationConfigurationWrapper>> {
        return runReadAction {
            val notebookPsiFile = virtualFile.file.toPsiFile(project)
            val ktFiles = notebookPsiFile.getInjectedKtFiles()
            val configurations = ktFiles.mapNotNull { ktFile ->
                val conf = JupyterKtScriptingSupport.getConfiguration(project, ktFile)?.valueOrNull()
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

    private fun KtFile.reportAsAttachment() {
        LOG.errorUnderDebug(
            "Empty script dependencies found",
            Attachment(
                virtualFilePath,
                text.takeIf { it.isNotEmpty() } ?: "[Injected file has no text]"
            )
        )
    }


    private fun getSession(): JupyterNotebookSession? {
        return try {
            if (!ApplicationManager.getApplication().isUnitTestMode) {
                runBlocking { JupyterRuntimeService.getInstance(project).getOrCreateSession(virtualFile) }
            } else null
        } catch (e: Throwable) {
            // TODO: show error for user with asking for configuring Python interpreter for the module
            if (e is ProcessCanceledException) throw e
            LOG.warn("Cannot create Jupyter session for Kotlin notebook", e)
            null
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
                compilerPublisher.scriptsClassesChanged(virtualFile)
                if (!ApplicationManager.getApplication().isUnitTestMode) {
                    JupyterKtScriptingSupport.updateSynchronously(project)
                }
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

        compileLock.write {
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
        return compileLock.write {
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

        compileLock.read {
            val withNewClasspath = config.withUpdatedClasspath(currentClasspath)
            return ScriptCompilationConfiguration(withNewClasspath) {
                if (_currentClasspath.hasInitialPart) {
                    addBaseClass<ScriptTemplateWithDisplayHelpers>()
                }

                hostConfiguration.update {
                    it.with {
                        getScriptingClass(classGetter)
                    }
                }
                implicitReceivers(implicitsList.reversed())
                defaultImports(additionalDefaultImports.getList())
                ide.dependenciesSources(
                    JvmDependency(
                        project.allSourceRoots() + _sourceRoots.getList()
                    )
                )
            }
        }
    }

    fun addCompiledSnippet(
        snippetMetadata: EvaluatedSnippetMetadata,
        psiCell: JupyterPsiCell?,
        updateAction: () -> Unit
    ) {
        KotlinNotebookPluginUpdater.getInstance().pluginUsed()
        // execute not on EDT
        AppExecutorUtil.getAppExecutorService().execute {
            try {
                compileLock.write {
                    addNewDependencies(snippetMetadata, psiCell)
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
        snippetMetadata: EvaluatedSnippetMetadata,
        psiCell: JupyterPsiCell?
    ) {
        val sessionId = getSession()?.sessionId

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
                    updateInjectedCellInfo(snippetMetadata, psiCell)
                }
            }
        }

        val kClassNames = deserializer.deserializeAndSave(snippetMetadata.compiledData, lineClassesDir, lineSourcesDir)
        loadReceiverClassesIfAny(lineClassesDir, kClassNames)
        //LOG.warn("Added new classes to load: $kClassNames")
    }

    fun provideDefaultConfiguration(sourceCode: SourceCode): ScriptCompilationConfigurationResult {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            coroutineScope.async {
                JupyterCompilerService.getInstance(project).requestScriptingUpdate()
            }
        }

        return ScriptCompilationConfigurationWrapper.FromCompilationConfiguration(
            sourceCode,
            lastStableConfiguration.get()
        ).asSuccess()
    }

    private fun createNextClassLoader(classesDirPath: Path): ClassLoader = URLClassLoader(
        arrayOf(classesDirPath.toUri().toURL()),
        (implicitsList.lastOrNull()?.fromClass ?: this::class).java.classLoader
    )

    private fun loadReceiverClassesIfAny(classesDirPath: Path, classesToLoad: Collection<String>): Boolean {
        val classesLoaded = compileLock.write {
            if (classesToLoad.isEmpty()) {
                return false
            }

            try {
                val loader = createNextClassLoader(classesDirPath)
                classesToLoad.forEach { className ->
                    LOG.debug("Adding class: $className")
                    val kClass = loader.loadClass(className).kotlin
                    implicitsList.addClass(kClass)
                }
            } catch (e: Throwable) {
                when (e) {
                    is ProcessCanceledException -> {
                        throw e
                    }
                    is UnsupportedClassVersionError -> {
                        val msg = e.message?.substringAfter("has been compiled by a more recent version of the Java Runtime") ?: ""
                        NotebookNotificationUtility.kernelRelatedFactory.showKernelJDKInconsistentError(project, msg)
                        return@write true
                    }
                    else -> LOG.error(e)
                }
                return@write false
            }
            true
        }

        return classesLoaded
    }

    internal fun changeCellsData(effectedIndexes: Collection<Int>,
                                 eventType: NotebookChangeEventsType,
                                 moveEvent: NotebookMoveEvent? = null,
                                 invokedMoveEventInInd: Int? = null) {
        if (effectedIndexes.isEmpty()
            || eventType != NotebookChangeEventsType.CELL_ADD && eventType != NotebookChangeEventsType.CELL_DELETE) return
        val presentRecords = cellOrdinalToClassName.filterKeys { it in effectedIndexes || it == invokedMoveEventInInd }.ifEmpty { return }
        val isAddEvent = eventType == NotebookChangeEventsType.CELL_ADD

        val (indexShift, keys) =
            if (isAddEvent) // go from last to first, e.g. shifting very last first
                1 to presentRecords.keys.sortedDescending()
            else -1 to presentRecords.keys.toList()

        val separatedByGaps = mutableListOf<MutableSet<Int>>().also {
            val consecutiveData = mutableSetOf<Int>()
            var ind = 0
            if (keys.size == 1) {
                consecutiveData.add(keys.first())
                it.add(consecutiveData)
                return@also
            }
            while (ind < keys.size - 1) {
                val first = keys[ind]
                val next = keys[ind + 1]
                if (abs(first - next) > 1) {
                    consecutiveData.add(first)
                    it.add(consecutiveData.toMutableSet())
                    consecutiveData.clear()
                    consecutiveData.add(next)
                } else {
                    consecutiveData.add(first)
                    if (ind + 1 == keys.size - 1) consecutiveData.add(next)
                }
                ind++
            }
            it.add(consecutiveData)
        }

        moveEvent?.let {
            val invokedInCell = invokedMoveEventInInd ?: return@let
            val storedData = cellOrdinalToClassName[invokedInCell]
            val isCellUp = it == NotebookMoveEvent.CELL_UP
            val anotherAffectedInd = if (isCellUp) invokedInCell - 1 else invokedInCell + 1
            // skip if it will be processed later
            separatedByGaps.firstOrNull { set -> invokedInCell in set || anotherAffectedInd in set }?.let { foundContainer ->
                foundContainer.removeIf { elem -> elem == invokedInCell || elem == anotherAffectedInd }
            }

            val storedInAnother = cellOrdinalToClassName[anotherAffectedInd]
            if (storedData != null) {
                cellOrdinalToClassName[anotherAffectedInd] = storedData
            } else cellOrdinalToClassName.remove(anotherAffectedInd)
            if (storedInAnother != null) {
                cellOrdinalToClassName[invokedInCell] = storedInAnother
            } else cellOrdinalToClassName.remove(invokedInCell)
        }


        val toRemove = mutableSetOf<Int>()
        for (consecutiveData in separatedByGaps) {
            consecutiveData.forEach { ind ->
                val data = presentRecords[ind] ?: return@forEach
                val newInd = ind + indexShift
                if (newInd >= 0) {
                    cellOrdinalToClassName[newInd] = data
                }
            }
            toRemove.addIfNotNull(consecutiveData.lastOrNull())
        }

        toRemove.forEach { cellOrdinalToClassName.remove(it) }
    }

    private fun updateInjectedCellInfo(snippetMetadata: EvaluatedSnippetMetadata, psiCell: JupyterPsiCell) {
        fun storeReferenceInfo(compiledClassName: MutableSet<String>, cellInd: Int?) {
            NotebookHighlightingService.getForFile(project, virtualFile)
                .dataController.invalidateStateAfterCellExecution(executedCellInd = cellInd)
            synchronized(psiCell) {
                val last = psiCell.getUserData(NotebookReferenceFinder.CELL_CLASS_NAME)?.firstOrNull()
                compiledClassName.addIfNotNull(last)
                psiCell.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, compiledClassName)
            }
        }
        val injectManager = InjectedLanguageManager.getInstance(project)
        val compilerService = JupyterCompilerService.getForFile(project, virtualFile)

        val compiledClassName = snippetMetadata.compiledData.sources.mapTo(mutableSetOf()) {
            it.fileName.substringBefore(".kts").let { f -> f + "_jupyter" }
        }
        var nextCellInd: Int? = null
        (psiCell.parent as? JupyterNotebook)?.psiCellList?.let { cells ->
            val executedCellInd = cells.indexOf(psiCell)
            if (executedCellInd != -1) {
                compilerService.cellOrdinalToClassName[executedCellInd] = compiledClassName
                nextCellInd = if (executedCellInd + 1 != cells.size) executedCellInd + 1 else null
            }
        }
        try {
            (injectManager.getInjectedPsiFiles(psiCell)?.firstOrNull()?.first as? PsiFile)
                ?.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, compiledClassName)
        } catch (ex: Exception) {
            if (ex is ProcessCanceledException) {
                coroutineScope.async {
                    storeReferenceInfo(compiledClassName, nextCellInd)
                }
                return
            } else LOG.warn("Exception during storing cell-related data", ex)
        }
        storeReferenceInfo(compiledClassName, nextCellInd)
    }

    private fun clearPreviousSnippets() {
        _currentClasspath.clear()
        additionalDefaultImports.clear()
        implicitsList.clear()
        cellOrdinalToClassName.clear()
        lastStableConfiguration.set(project.baseScriptingCompilationConfiguration)
    }

    fun clear() {
        compileLock.write {
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

    companion object {
        private val LOG = Logger.getInstance(JupyterCompilerPerFileService::class.java)
    }
}
