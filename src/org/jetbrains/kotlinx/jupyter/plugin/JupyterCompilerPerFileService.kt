// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin

import com.fasterxml.jackson.databind.node.ArrayNode
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.configurationStore.runAsWriteActionIfNeeded
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.nullize
import com.intellij.util.io.delete
import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.core.script.ClasspathToVfsConverter
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.CompiledScriptsSerializer
import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.config.defaultGlobalImports
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.NoopMagicsHandler
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService.Companion.SCRIPT_DEPENDENCIES_LIBRARY_NAME
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinProjectArtifactsService.Companion.buildProjectAndGetLibraries
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingRestarter.UpdateSteps.postScriptingUpdateStep
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookCellsUpdatesAllowedToChange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.invalidateStateAfterCellExecution
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.file.psi.NotebookReferenceFinder
import org.jetbrains.kotlinx.jupyter.plugin.file.toDocument
import org.jetbrains.kotlinx.jupyter.plugin.file.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKotlinPluginScriptClassGetter
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.kotlinx.jupyter.plugin.scripting.NotebookChangeEventsType
import org.jetbrains.kotlinx.jupyter.plugin.scripting.NotebookMoveEvent
import org.jetbrains.kotlinx.jupyter.plugin.session.KotlinKernelProcessService
import org.jetbrains.kotlinx.jupyter.plugin.stats.KotlinNotebookPluginUpdater
import org.jetbrains.kotlinx.jupyter.plugin.util.KernelJarsProvider
import org.jetbrains.kotlinx.jupyter.plugin.util.allJarsFromDir
import org.jetbrains.kotlinx.jupyter.plugin.util.allSourceRoots
import org.jetbrains.kotlinx.jupyter.plugin.util.tryWithWriteLock
import org.jetbrains.kotlinx.jupyter.plugin.util.withReadLock
import org.jetbrains.kotlinx.jupyter.plugin.util.withWriteLock
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterNotebook
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterSource
import java.io.File
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
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
    private var psiFile = runReadAction {
        virtualFile.file.toPsiFile(project)
    }
    private val compileLock = ReentrantReadWriteLock()
    private val listLock = ReentrantReadWriteLock()
    private val directoryCounter = AtomicInteger(1)
    private val nbInjectionHosts: MutableSet<PsiLanguageInjectionHost> = ConcurrentCollectionFactory.createConcurrentSet() // LoggingList()
    private val implicitListsLoadQueue = ArrayDeque<Pair<Path, List<String>>>()
    val cellOrdinalToClassName = mutableMapOf<Int, Set<String>>()

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

    private var kernelJarsAdded: Boolean = false
    private val kernelJarsProviders: Collection<KernelJarsProvider> = listOf(
        KernelJarsProvider {
            KotlinKernelProcessService.getInstance().ideJars
        },
        KernelJarsProvider {
            LOG.warn("Kernel jars were requested from running Jupyter session...")
            getSession()?.detectKotlinKernelJarsDir()?.allJarsFromDir().orEmpty()
        },
    )

    private val implicitsList = KotlinImplicitReceiversList()
    private val classGetter = JupyterKotlinPluginScriptClassGetter(ScriptTemplateWithDisplayHelpers::class) {
        implicitsList
    }

    private val coroutineScope = CoroutineScope(Job())
    private var previousSessionId: String? = null

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
            invokeLater(ModalityState.NON_MODAL) {
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

        updateClasspathWithExternalDependencies()
        Disposer.register(parent, this)
    }

    private fun getSession(): JupyterNotebookSession? {
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

    private fun updateClasspathWithExternalDependencies() {
        updateClasspathWithKernelJars()
        updateClasspathWithProjectArtifactsAsync()
    }

    private fun updateClasspathWithKernelJars() {
        if (kernelJarsAdded) return

        compileLock.write {
            if (kernelJarsAdded) return
            kernelJarsProviders.firstNotNullOfOrNull { provider ->
                provider.getKernelJars()
            }?.let { jars ->
                val sourcesJars = KotlinKernelProcessService.getInstance().libSourcesJars
                _currentClasspath.addInitial(jars)
                _sourceRoots.addInitial(sourcesJars)
                val application = ApplicationManager.getApplication()
                if (!application.isUnitTestMode) {
                    application.invokeLaterOnWriteThread {
                        addAsPermanentLibrary(jars.map { it.absolutePath }, sourcesJars.map { it.absolutePath })
                    }
                }
                kernelJarsAdded = true
            }
        }
    }

    private fun updateClasspathWithProjectArtifactsAsync() {
        coroutineScope.async {
            val buildService = JupyterKotlinProjectArtifactsService.getInstance(project)
            val artifacts = buildService.buildProjectAndGetLibraries(virtualFile).ifEmpty { return@async }
            val updated = compileLock.withWriteLock {
                val oldSize = _currentClasspath.size
                _currentClasspath.addSnippet(artifacts.map { File(it) })
                val newSize = _currentClasspath.size
                oldSize != newSize
            }
            if (updated) {
                JupyterKtScriptingSupport.update(project)
            }
        }
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

    private fun addAsPermanentLibrary(classpath: List<String>, sourceClasspath: List<String>) {
        val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)

        val newLibrary = libraryTable.getLibraryByName(SCRIPT_DEPENDENCIES_LIBRARY_NAME)
            ?: invokeAndWaitIfNeeded {
                runAsWriteActionIfNeeded {
                    libraryTable.getLibraryByName(SCRIPT_DEPENDENCIES_LIBRARY_NAME) ?: libraryTable.createLibrary(SCRIPT_DEPENDENCIES_LIBRARY_NAME)
                }
            }

        val model = newLibrary.modifiableModel
        val existingRoots = buildMap<OrderRootType, Set<String>> {
            for (rootType in listOf(OrderRootType.CLASSES, OrderRootType.SOURCES)) {
                put(rootType, newLibrary.rootProvider.getUrls(rootType).toSet())
            }
        }

        fun addPath(path: String, rootType: OrderRootType) {
            if (path.endsWith(".jar")) {
                val rootPath = "file://${File(path).invariantSeparatorsPath}"
                if (existingRoots[rootType]!!.contains(rootPath)) return
                model.addRoot(rootPath, rootType)
            }
        }

        for (path in classpath) {
            addPath(path, OrderRootType.CLASSES)
        }
        for (path in sourceClasspath) {
            addPath(path, OrderRootType.SOURCES)
        }
        invokeLater {
            runAsWriteActionIfNeeded {
                model.commit()
            }
        }
    }

    fun addCompiledSnippet(
        snippetMetadata: EvaluatedSnippetMetadata,
        psiCell: JupyterPsiCell?,
    ) {
        compileLock.withWriteLock {
            try {
                KotlinNotebookPluginUpdater.getInstance().pluginUsed()

                val sessionId = ApplicationManager.getApplication().executeOnPooledThread<String?> {
                    getSession()?.sessionId
                }.get()

                if (sessionId != previousSessionId) {
                    LOG.info("Clearing Kotlin snippets. Previous session ID: $previousSessionId")
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

                AppExecutorUtil.getAppExecutorService().execute {
                    addAsPermanentLibrary(snippetMetadata.newClasspath, snippetMetadata.newSources)
                    if (psiCell != null) {
                        runReadAction {
                            updateInjectedCellInfo(snippetMetadata, psiCell)
                        }
                    }
                }

                val kClassNames = deserializer.deserializeAndSave(snippetMetadata.compiledData, lineClassesDir, lineSourcesDir)
                implicitListsLoadQueue.addLast(Pair(lineClassesDir, kClassNames))
                needsToUpdate.set(true)
            } catch (e: Exception) {
                LOG.error(e)
            }
        }
    }

    fun updateScripting() {
        compileLock.withWriteLock {
            //updateCellsAnalysis()
            virtualFile.file.toDocument()
                ?.getUserData(NotebookCellsUpdatesAllowedToChange)
                ?.compareAndSet(true, false)
            JupyterKtScriptingSupport.update(project)
        }
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
            postScriptingUpdateStep(NotebookHighlightingService.getForFile(project, virtualFile).document)
        }
    }

    fun loadReceiverClassesIfAny(document: Document? = null, shouldUpdateImmediately: Boolean = false): Boolean {
        var loadedSize: Int = 0
        return compileLock.tryWithWriteLock {
            if (implicitListsLoadQueue.isEmpty()) {
                //needsToUpdate.compareAndSet(true, false)
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
                            NotebookNotificationUtility.showKernelJDKInconsistentError(project, msg)
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
                    LOG.debug("Requesting update of scripting after loading new classes in ${psiFile?.name}, loaded: $loadedSize")
                    document?.getUserData(NotebookCellsUpdatesAllowedToChange)?.compareAndSet(true, false)
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
        val topLevelFile = when {
            psiFile?.isValid == true -> psiFile
            psiCell.containingFile.isValid -> psiCell.containingFile
            else -> InjectedLanguageManager.getInstance(project).getTopLevelFile(psiCell)
        }?.also {
            psiFile = it
        }
        val document = topLevelFile?.virtualFile?.let {
            FileDocumentManager.getInstance().getDocument(it)
        }

        val properCompiledClass = snippetMetadata.compiledData.sources.mapTo(mutableSetOf()) {
            it.fileName.substringBefore(".kts").let { f -> f + "_jupyter" }
        }
        var nextCellInd: Int? = null
        (psiCell.parent as? JupyterNotebook)?.psiCellList?.let { cells ->
            val executedCellInd = cells.indexOf(psiCell)
            if (executedCellInd != -1) {
                compilerService.cellOrdinalToClassName[executedCellInd] = properCompiledClass
                nextCellInd = if (executedCellInd + 1 != cells.size) executedCellInd + 1 else null
            }
        }
        try {
            (injectManager.getInjectedPsiFiles(psiCell)?.firstOrNull()?.first as? PsiFile)
                ?.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, properCompiledClass)
        } catch (ex: Exception) {
            LOG.warn("Exception during storing cell-related data", ex)
        }
        document
            ?.invalidateStateAfterCellExecution(executedCellInd = nextCellInd) // need to highlight next cell if ok
        synchronized(psiCell) {
            val last = psiCell.getUserData(NotebookReferenceFinder.CELL_CLASS_NAME)?.firstOrNull()
            properCompiledClass.addIfNotNull(last)
            psiCell.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, properCompiledClass)
        }
    }

    private fun getCellCode(cell: PsiElement): String {
        val sourceElement = PsiTreeUtil.getChildOfType(cell, JupyterSource::class.java)
        val source = sourceElement?.text.orEmpty()
        return source.trimStart()
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
        cellOrdinalToClassName.clear()
    }

    fun clear() {
        compileLock.write {
            clearPreviousSnippets()

            nbInjectionHosts.clear()
            classesDir.delete(true)
            coroutineScope.cancel()
            implicitListsLoadQueue.clear()
        }

        ClasspathToVfsConverter.clearCaches()

        val manager = ScriptConfigurationManager.getInstance(project) as? CompositeScriptConfigurationManager
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

        val size: Int get() = initialPart.size + snippetsPart.size

        fun clear() {
            lock.withWriteLock { snippetsPart.clear() }
        }

        fun addInitial(items: Collection<T>) {
            lock.withWriteLock { initialPart.addAll(items) }
        }

        fun addSnippet(items: Collection<T>) {
            lock.withWriteLock { snippetsPart.addAll(items) }
        }

        fun getList(): List<T> {
            return lock.withReadLock { (initialPart + snippetsPart).distinct() }
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
