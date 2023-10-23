// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
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
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.core.script.ClasspathToVfsConverter
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.config.defaultGlobalImports
import org.jetbrains.kotlinx.jupyter.plugin.editor.find.NotebookReferenceFinder
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.editor.notifications.NotebookNotificationUtility
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService.Companion.buildProjectAndGetLibraries
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.KotlinNotebookPermanentIndexService
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifactsDownloader
import org.jetbrains.kotlinx.jupyter.plugin.statistics.usages.KotlinNotebookPluginUpdater
import org.jetbrains.kotlinx.jupyter.plugin.util.ComputableWithName
import org.jetbrains.kotlinx.jupyter.plugin.util.ExecutedOnceBackgroundTask
import org.jetbrains.kotlinx.jupyter.plugin.util.allSourceRoots
import org.jetbrains.kotlinx.jupyter.plugin.util.anyOf
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.restartAnalyzing
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.util.tryWithWriteLock
import org.jetbrains.kotlinx.jupyter.plugin.util.withReadLock
import org.jetbrains.kotlinx.jupyter.plugin.util.withWriteLock
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.abs
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
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
    initialClasspath: List<File>,
    parent: Disposable
) : Disposable {
    private var isDisposed = false

    private val compileLock = ReentrantReadWriteLock()
    private val listLock = ReentrantReadWriteLock()
    private val directoryCounter = AtomicInteger(0)
    private val nbInjectionHosts: MutableSet<PsiLanguageInjectionHost> = ConcurrentCollectionFactory.createConcurrentSet()
    private val implicitListsLoadQueue = ArrayDeque<Pair<Path, List<String>>>()
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

    val executedCellsCount: Int get() = directoryCounter.get()

    fun scripts(): List<Pair<VirtualFile, ScriptCompilationConfigurationWrapper>> {
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val fileDocumentManager = FileDocumentManager.getInstance()
        val attemptsLimit = 3
        for (attempt in 1..attemptsLimit) {
            val res = runReadAction {
                val injectedManager = InjectedLanguageManager.getInstance(project)
                readInjectionHosts { hosts ->
                    hosts?.flatMap { host ->
                        injectedManager
                            .getInjectedPsiFiles(host)
                            .orEmpty()
                            .map { it.first }
                            .filterIsInstance<KtFile>()
                            .mapNotNull { ktFile ->
                                val conf = JupyterKtScriptingSupport.getConfiguration(project, ktFile)?.valueOrNull()
                                if (conf != null) (ktFile.virtualFile to conf) else null
                            }
                    }
                }
            }
            if (res != null) return res
            val document = runReadAction {
                fileDocumentManager.getDocument(virtualFile.file)
            } ?: return emptyList()

            val commitNotifier = CountDownLatch(1)
            // We commit document here and hope that Jupyter file will be reparsed,
            // and injection hosts will be recollected on this reparse
            invokeLater(ModalityState.nonModal()) {
                try {
                    psiDocumentManager.commitDocument(document)
                } finally {
                    commitNotifier.countDown()
                }
            }
            if (!commitNotifier.await(30, TimeUnit.SECONDS)) {
                LOG.error("Too long wait for document to commit", Throwable())
                return emptyList()
            }
        }

        LOG.error("No luck in obtaining notebook's scripts in $attemptsLimit attempts")
        return emptyList()
    }

    init {
        thisLogger().assertTrue(virtualFile.file.isKotlinNotebook) { "$virtualFile is not a Kotlin Jupyter notebook" }
        Disposer.register(parent, this)

        externalDependenciesProvider.startIfNotStarted()
        // We need to ensure we have all dependencies before the test started
        if (ApplicationManager.getApplication().isUnitTestMode) {
            externalDependenciesProvider.join()
        }
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
                JupyterKtScriptingSupport.updateSynchronously(project)
                restartAnalyzing(project, virtualFile.file)
            }
        }
    }

    private suspend fun updateClasspathWithKernelJars(): Boolean {
        val mavenArtifactsDownloader = KotlinNotebookMavenArtifactsDownloader.getInstance(project)
        val jars = mavenArtifactsDownloader.downloadArtifactAsync(KotlinNotebookMavenArtifacts.IDE_CLASSPATH_SHADOWED)
        val sourcesJars = mavenArtifactsDownloader.downloadArtifactAsync(KotlinNotebookMavenArtifacts.SCRIPT_CLASSPATH_SHADOWED_SOURCES)

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
        sourceCode: SourceCode,
        config: ScriptCompilationConfiguration
    ): ScriptCompilationConfiguration {
        val sourceText = runReadAction { sourceCode.text }
        LOG.debug("Before-compiling callback for script: $sourceText")
        if (!externalDependenciesProvider.isCompletedSuccessfully) return config

        val withNewClasspath = config.withUpdatedClasspath(currentClasspath)
        return ScriptCompilationConfiguration(withNewClasspath) {
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

    private fun <R> readInjectionHosts(readAction: (Collection<PsiLanguageInjectionHost>?) -> R): R {
        return listLock.read {
            val hosts = if (nbInjectionHosts.isEmpty()) {
                emptyList()
            } else if (!nbInjectionHosts.first().containingFile.isValid) {
                null
            } else {
                nbInjectionHosts
            }
            readAction(hosts)
        }
    }

    fun updateInjectionHosts(updateAction: (MutableCollection<PsiLanguageInjectionHost>) -> Unit) {
        listLock.write {
            updateAction(nbInjectionHosts)
        }
    }

    fun addCompiledSnippet(
        snippetMetadata: EvaluatedSnippetMetadata,
        psiCell: JupyterPsiCell?,
    ) {
        KotlinNotebookPluginUpdater.getInstance().pluginUsed()
        // execute not on EDT
        AppExecutorUtil.getAppExecutorService().execute {
            compileLock.withWriteLock {
                try {
                    addNewDependencies(snippetMetadata, psiCell)
                } catch (e: Exception) {
                    if (e is ProcessCanceledException) {
                        throw e
                    }
                    LOG.error(e)
                }
            }
        }
    }

    fun updateScripting() {
        compileLock.withWriteLock {
            //updateCellsAnalysis()
            NotebookHighlightingService.getForFile(project, virtualFile)
                .beforeScriptingUpdate()
            JupyterKtScriptingSupport.update(project)
        }
    }

    private fun addNewDependencies(snippetMetadata: EvaluatedSnippetMetadata, psiCell: JupyterPsiCell?) {
        val sessionId = getSession()?.sessionId

        if (sessionId != previousSessionId) {
            LOG.info("Clearing Kotlin snippets. Previous session ID: ${previousSessionId?.id}")
            clearPreviousSnippets()
            previousSessionId = sessionId
        }

        // TODO: compare text in snippet metadata with cell source and add a source file to directory and to the container
        val nextCounter = directoryCounter.incrementAndGet()

        val lineClassesDir = classesDir.resolve("line_$nextCounter")
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
            runReadAction {
                updateInjectedCellInfo(snippetMetadata, psiCell)
            }
        }

        val kClassNames = deserializer.deserializeAndSave(snippetMetadata.compiledData, lineClassesDir, lineSourcesDir)
        implicitListsLoadQueue.addLast(Pair(lineClassesDir, kClassNames))
        needsToUpdate.set(true)
        //LOG.warn("Added new classes to load: $kClassNames")
    }

    private fun createNextClassLoader(classesDirPath: Path): ClassLoader = URLClassLoader(
        arrayOf(classesDirPath.toUri().toURL()),
        (implicitsList.lastOrNull()?.fromClass ?: this::class).java.classLoader
    )

    val hasPendingUpdates: Boolean get() = needsToUpdate.get()
    private val needsToUpdate = AtomicBoolean(false)

    fun afterScriptingUpdate() {
        needsToUpdate.set(false)
        val isEmpty = compileLock.withReadLock { implicitListsLoadQueue.isEmpty() }
        if (isEmpty) {
            NotebookHighlightingService.getForFile(project, virtualFile).afterScriptingUpdate()
        }
    }

    fun loadReceiverClassesIfAny(shouldUpdateImmediately: Boolean = false): Boolean {
        var loadedSize = 0
        return compileLock.tryWithWriteLock {
            if (implicitListsLoadQueue.isEmpty()) {
                return false
            }
            loadedSize = implicitListsLoadQueue.size

            while (implicitListsLoadQueue.isNotEmpty()) {
                val firstElem = implicitListsLoadQueue.firstOrNull()
                if (firstElem == null) {
                    return@tryWithWriteLock needsToUpdate.get()
                }
                val (lineDir, classes) = firstElem
                try {
                    val loader = createNextClassLoader(lineDir)
                    classes.forEach { className ->
                        LOG.debug("Adding class: $className")
                        val kClass = loader.loadClass(className).kotlin
                        implicitsList.addClass(kClass)
                    }
                    implicitListsLoadQueue.removeFirstOrNull()
                    needsToUpdate.set(true)
                } catch (e: Throwable) {
                    when (e) {
                        is ProcessCanceledException -> {
                            throw e
                        }
                        is UnsupportedClassVersionError -> {
                            val msg = e.message?.substringAfter("has been compiled by a more recent version of the Java Runtime") ?: ""
                            NotebookNotificationUtility.kernelRelatedFactory.showKernelJDKInconsistentError(project, msg)
                        }
                        is ClassNotFoundException -> {
                            implicitListsLoadQueue.removeFirstOrNull()
                            LOG.error(e)
                        }
                        else -> LOG.error(e)
                    }
                    return@tryWithWriteLock true
                }
            }
            return@tryWithWriteLock true
        }.apply {
            if (this == true && shouldUpdateImmediately) {
                coroutineScope.async {
                    val psiFile = readAction {
                        virtualFile.file.toPsiFile(project)
                    }
                    LOG.debug("Requesting update of scripting after loading new classes in ${psiFile?.name}, loaded: $loadedSize")
                    NotebookHighlightingService.getForFile(project, virtualFile).beforeScriptingUpdate()
                    withContext(Dispatchers.EDT) {
                        JupyterKtScriptingSupport.update(project)
                    }
                }
            }
        } ?: false
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
            LOG.warn("Exception during storing cell-related data", ex)
        }
        NotebookHighlightingService.getForFile(project, virtualFile)
            .dataController.invalidateStateAfterCellExecution(executedCellInd = nextCellInd)
        synchronized(psiCell) {
            val last = psiCell.getUserData(NotebookReferenceFinder.CELL_CLASS_NAME)?.firstOrNull()
            compiledClassName.addIfNotNull(last)
            psiCell.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, compiledClassName)
        }
    }

    private fun clearPreviousSnippets() {
        _currentClasspath.clear()
        additionalDefaultImports.clear()
        implicitsList.clear()
        cellOrdinalToClassName.clear()
    }

    fun clear() {
        compileLock.write {
            clearPreviousSnippets()

            classesDir.delete(true)
            coroutineScope.cancel()
            implicitListsLoadQueue.clear()
        }

        listLock.write {
            nbInjectionHosts.clear()
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
