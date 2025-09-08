// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.hints.CollectorWithSettings
import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSinkImpl
import com.intellij.codeInsight.hints.LinearOrderInlayRenderer
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.injected.editor.EditorWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.executor.JupyterExecutionManager
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.util.getInjectedKtFiles
import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrSelf
import com.intellij.kotlin.jupyter.core.util.toAbsolutePath
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import com.intellij.kotlin.jupyter.test.notebook.codeinsight.intentions.IntentionInvocationHandler
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessages
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessagesTester
import com.intellij.kotlin.jupyter.test.notebook.execution.buildJacksonObject
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.startOffset
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ArrayUtilRt
import com.intellij.util.asSafely
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import junit.framework.AssertionFailedError
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.github.GithubGistContentsCollector
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Class providing a simpler API for Kotlin Notebook tests, making it possible to define the test flow as
 * seen from a user perspective.
 *
 * If a `<caret>` marker is in the test file, it will be placed there. Otherwise, it will be placed
 * at the beginning of the first cell.
 */
class NotebookTestBuilder(
    private val project: Project,
    val notebookFile: PsiFile,
    private val testFixture: CodeInsightTestFixture,
    private val testCase: KotlinNotebookTestCase,
) {

    companion object {
        val LOG = thisLogger()
    }

    // Returns the number of cells (of all types) in the notebook
    val cellCount: Int
        get() {
            return notebookFile.getCells().count()
        }


    // State required to track if highlighting has been started
    private var highlighterDaemonStarted: Boolean = false
    private val highlightSetup = {
        (DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl).prepareForTest()
        DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled = false
        highlighterDaemonStarted = true
    }
    private val highlightCleanup = {
        DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled = true
        val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
        daemonCodeAnalyzer.cleanupAfterTest()
    }

    // State required for completion tests
    private val completionTester = CompletionAutoPopupTester(testFixture)

    // State required for triggering Intentions
    private var intentionInvocationHandler: IntentionInvocationHandler = IntentionInvocationHandler(testFixture)

    // State required to track if a Jupyter Session has been started
    private var jupyterSessionStarted: Boolean = false
    private val notebookBackedFile: BackedNotebookVirtualFile

    private var jupyterSession: JupyterNotebookSession? = null
    private val jupyterSessionSetup = suspend {
        val project = notebookFile.project
        jupyterSession = JupyterExecutionManager.getInstance(project, notebookFile.virtualFile).getOrCreateSession()

    }
    private val jupyterSessionCleanup = {
        runBlocking {
            jupyterSession?.deleteSession()
        }
        // make sure to drop previous data
        JupyterCompilerService.getInstance(project).removeSession(notebookBackedFile)
    }

    init {
        notebookBackedFile = notebookFile.virtualFile.toKotlinNotebookBackedFile()
            ?: error("Failed to convert file to notebook backed file: ${notebookFile.virtualFile.toAbsolutePath()}")
    }

    /**
     * Wait for the initial script dependencies to be available on the classpath.
     * They include the Kotlin stdlib and script definitions.
     */
    fun setupScriptDependencies() {
        // This approach should work for both K1 and K2
        testCase.setUpDependenciesSynchronously(0)
        waitForReadyIndexes(testFixture)
    }

    /**
     * Wait for the notebook to reach "steady state" after cell execution.
     *
     * This means waiting for all queued cells to be executed and the notebook classpath to be
     * updated with the result.
     *
     * @param executedCells the number of cells that has been executed.
     */
    fun waitForDependencies(executedCells: Int = 1) {
        testCase.setUpDependenciesSynchronously(executedCells)
        waitForReadyIndexes(testFixture)
    }

    // Tear down resources used by the runner (called at the end of the test),
    // Exceptions should be thrown to the caller who will handle them. (TODO Is this correct)
    fun tearDown() {
        if (highlighterDaemonStarted) {
            highlightCleanup()
        }
        if (jupyterSessionStarted) {
            jupyterSessionCleanup()
        }
    }

    /**
     * Execute a cell and return the output result.
     *
     * @param cellIndex which cell to execute. The starting cell is 0.
     * @param waitForDependencies if `true`, the method will block until the IDE classpath as been
     * updated with the newly compiled snippet as well as any potential new dependencies. Default
     * is `false` to match IDE behavior.
     * @return output as a JSON object. If the cell generates no output, an empty object is returned.
     */
    suspend fun executeCell(
        cellIndex: Int,
        waitForDependencies: Boolean = false,
    ): ExecutionResult {
        if (!jupyterSessionStarted) {
            jupyterSessionSetup()
            jupyterSessionStarted = true
        }

        // Jcef is not supported on Team City
        return withDisabledJcef {
            var output: ObjectNode? = null
            executeCells(
                tester = object : ReceivedMessagesTester {
                    override val cellsToExecute: List<Int> = listOf(cellIndex)
                    override val expectedCellsCount: Int get() = cellCount
                    override fun assertCellMessages(
                        cellNum: Int,
                        messages: ReceivedMessages
                    ) {
                        output = messages.outputs
                            .map { it.messageContent["data"] as? ObjectNode }
                            .singleOrNull() ?: buildJacksonObject { /* Empty object */ }
                    }
                },
                notebookFile = notebookFile,
                executionCallback = null
            )
            if (waitForDependencies) {
                waitForDependencies(executedCells = 1)
            }
            ExecutionResult(output!!)
        }
    }

    /**
     * Move the editor caret to the given cell index and place it at [tokenOffset]
     * in the cell. The default is the caret being placed at the start of the cell.
     */
    suspend fun moveCaretToCell(cellIndex: Int, tokenOffset: Int = 0) {
        withContext(Dispatchers.EDT) {
            val nextCell = notebookFile.getCells()[cellIndex]
            testFixture.editor.moveCaret(nextCell.startOffset + tokenOffset)
        }
    }

    /**
     * Returns the visible content of the current active cell.
     *
     * If no cells are active, e.g., if [NotebookEditorMode.COMMAND] is set, this method will throw
     * an [AssertionFailedError].
     */
    val currentCellContent: String
        get() {
            return runInEdtAndGet {
                // The test fixture editor contains Notebook language (which uses #%% as cell start), and not the
                // injected language. This is closer to the actual content of the file, but also means we need to
                // strip cell markers to get the user visible content.
                val currentCellContent = testFixture.editor.document.text
                currentCellContent.replace(Regex("^(#%%|#%% md|#%% sql)\\n"), "")
            }
        }

    /**
     * Returns the content of the notebook file as a string using the Notebook Language. This means that
     * all cells (including the first) start with `#%%` (and its variants) markers.
     */
    val notebookContent: String
        get() = runInEdtAndGet {
            notebookFile.text
        }

    /**
     * Returns the injected file that represents a Notebook cell.
     * An error is thrown if the cell is not found.
     */
    fun getInjectedFileForCell(cellIndex: Int): PsiFile {
        val cells: List<JupyterPsiCell> = notebookFile.getCells()
        val neededCell = cells.getOrNull(cellIndex) ?: error("Invalid cell index provided: $cellIndex. Total cells: $cellCount")
        return ReadAction.compute<PsiFile, Throwable> {
            (InjectedLanguageManager.getInstance(project)
                .getInjectedPsiFiles(neededCell)?.firstOrNull { it.first.containingFile.isInjectedKtFile() }?.first as? PsiFile)
        }
    }

    /**
     * Run highlighting on the notebook and return the result of it.
     */
    suspend fun runHighlighting(): HighlightingResult {
        if (!highlighterDaemonStarted) {
            highlightSetup()
        }
        val results = withContext(Dispatchers.EDT) {
            doHighlighting()
        }
        return HighlightingResult(notebookFile, testFixture, results)
    }

    /**
     * Execute an editor action on the EDT thread.
     */
    suspend fun performEditorAction(actionId: String) {
        // There doesn't seem to be an obvious way to check if a given action actually exists.
        with(Dispatchers.EDT) {
            testFixture.performEditorAction(actionId)
        }
    }

    /**
     * Trigger autocomplete at the current caret position.
     */
    fun completeAtCaret(type: CompletionType = CompletionType.BASIC): CompletionResult {
        val result = testFixture.complete(type)
        return CompletionResult(result)
    }

    /**
     * Type text into the active cell at the current caret and perform code completion after the text,
     * but do not manually complete it (It might complete automatically, see [CompletionResult]).
     *
     * @param string text to type into the cell.
     * @param waitFor How long to wait for completion to finish. The current result is returned after this period.
     */
    suspend fun typeAndGetLookup(
        string: String,
        waitFor: Duration = 15.seconds
    ): CompletionResult {
        var result: List<LookupElement>? = emptyList()
        typeAndDoWithLookup(string, { true }, waitFor) {
            result = it
        }
        return CompletionResult(result)
    }

    /**
     * Type text into the active cell at the current caret and perform code completion after the text.
     * Find the first element that matches [filter] and complete it using [mode].
     *
     * @Return A [CompletionResult] containing the list of lookups that matches [filter].
     */
    fun typeAndFinishLookup(
        string: String,
        mode: LookupFinishMode = LookupFinishMode.ENTER,
        filter: (LookupElement) -> Boolean
    ): CompletionResult {
        val result = AtomicReference<CompletionResult?>(null)
        // Unclear why we need this?
        completionTester.runWithAutoPopupEnabled {
            var finalLookupElements: List<LookupElement>? = null
            var completedWith: LookupElement? = null
            runBlocking {
                typeAndDoWithLookup(string, { filter(it) }) { lookupElements ->
                    if (lookupElements == null) return@typeAndDoWithLookup
                    val firstLookupElement = lookupElements.firstOrNull()
                    if (firstLookupElement == null) {
                        fail("No elements matching filter: ${lookupElements.map { it.lookupString }}")
                    }
                    completionTester.lookup.finishLookup(mode.completionChar, firstLookupElement)
                    finalLookupElements = lookupElements
                    completedWith = firstLookupElement
                }
            }
            result.set(CompletionResult(finalLookupElements, completedWith))
        }
        return result.get() ?: error("Failed to complete lookup")
    }

    /**
     * Write a given string at the current caret position.
     * @param text text to write into the cell.
     */
    fun type(text: String) {
        testFixture.type(text)
    }

    /**
     * Assert that the clipboard contains the given string.
     * This method will fail if the clipboard is empty or does not contain the given string.
     */
    fun assertClipboardContent(stringContent: String) {
        val actualContents: StringSelection = CopyPasteManager.getInstance().contents.asSafely<StringSelection>()
            ?: error("Expected buffer to contain string selection, but it doesn't: ${CopyPasteManager.getInstance().contents}")
        assertEquals(stringContent, actualContents.getTransferData(DataFlavor.stringFlavor))
    }

    /**
     * Trigger the provided [InlayHintsProvider] on the provided cell.
     * The resulting inlays are returned in [InlayHintsResult].
     */
    fun <T : Any> runInlayProvider(
        provider: InlayHintsProvider<T>,
        cellIndex: Int,
        setupAction: (T) -> Unit = {}
    ): InlayHintsResult {
        val cells = notebookFile.getCells()
        val neededCell = cells.getOrNull(cellIndex) ?: error("Invalid cell index provided: $cellIndex")
        val ktFile = ReadAction.compute<KtFile, Throwable> {
            neededCell.toInjectedKtFiles().first()
        }
        val fileOffset = 0 // Injected files start from 0
        val inlayResult = with(provider) {
            val settings = createSettings()
            setupAction(settings)
            invokeAndWaitIfNeeded {
                runReadAction {
                    testFixture.doHighlighting()
                    val sourceText = ktFile.text
                    dumpInlayHints(sourceText, provider, fileOffset, settings)
                }
            }
        }
        return InlayHintsResult(testCase, inlayResult)
    }

    /**
     * Run a preconfigured intention in the current active cell at the current caret position.
     * An error is thrown if the cell doesn't have an intention configured or the intention cannot
     * be found.
     *
     * The intention to run can be configured by using one of two special directives at the top of the cell.
     * See [IntentionInvocationHandler.invokeIntentionsInFile] for details on the syntax.
     *
     * Note, only one directive is allowed in a cell
     */
    suspend fun runIntentionInActiveCell() {
        withContext(Dispatchers.EDT) {
            val injectedFile = testCase.getKtFileUnderCaret() ?: error("File not found at caret position")
            intentionInvocationHandler.invokeIntentionsInFile(injectedFile)
        }
    }

    /**
     * Similar to [runIntentionInActiveCell], but pass in the Intention to run rather than reading it
     * from the special directive.
     */
    suspend fun runIntention(intention: IntentionAction) {
        withContext(Dispatchers.EDT) {
            val injectedFile = testCase.getKtFileUnderCaret() ?: error("File not found at caret position")
            intentionInvocationHandler.invokeIntention(injectedFile, intention)
        }
    }

    /**
     * Check if the intention is available in the current active cell and the given
     * caret position. If the availability is different from the [isAvailable] parameter,
     * an error is thrown.
     *
     * @param name The fully qualified name of the intention to check for.
     * @param isAvailable Whether the intention is expected to be available.
     */
    suspend fun checkIntention(name: String? = null, isAvailable: Boolean = true) {
        withContext(Dispatchers.EDT) {
            intentionInvocationHandler.checkIntention(name, isAvailable)
        }
    }

    /**
     * Export the notebook as a Gist.
     *
     * Note, this does not export the Gist, but only calls the underlying logic and returns the result
     * that would be sent to GitHub.
     */
    fun exportAsGist(): GithubGistRequest.FileContent {
        val gists = GithubGistContentsCollector.collectContents(project, testFixture.editor, testFixture.file.virtualFile, null)
        return gists.single() // We only ever export a single file, so this should be safe.
    }

    /**
     * Calls the "Reformat Code" action.
     * This will reformat the current cell.
     */
    fun reformatCode() {
        testFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)
    }

    /**
     * Calls the "Reformat File" action.
     * This will reformat all cells in the notebook.
     */
    fun reformatFile() {
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(testFixture.file.getTopLevelFileOrSelf())
        }
    }

    /**
     * Paste the given [text] in at the current caret position, like it was pasted in through the clipboard.
     */
    fun pasteFromClipboard(text: String) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
        testFixture.performEditorAction(IdeActions.ACTION_PASTE)
    }

    private fun doHighlighting(
        canChangeDocumentDuringHighlighting: Boolean = false,
        shouldDoFolding: Boolean = true,
        shouldDoInspections: Boolean = true,
    ): List<HighlightInfo> {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val toIgnoreList = mutableListOf<Int>()
        if (shouldDoFolding) {
            toIgnoreList.add(Pass.UPDATE_FOLDING)
        }
        if (shouldDoInspections) {
            toIgnoreList.add(Pass.LOCAL_INSPECTIONS)
        }
        val toIgnore = if (toIgnoreList.isEmpty()) ArrayUtilRt.EMPTY_INT_ARRAY else toIgnoreList.toIntArray()
        var editor = testFixture.editor
        var file = testFixture.file
        if (editor is EditorWindow) {
            editor = editor.delegate
            file = InjectedLanguageManager.getInstance(file.project).getTopLevelFile(file)
        }
        return CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, toIgnore, canChangeDocumentDuringHighlighting)
    }

    private suspend fun typeAndDoWithLookup(
        string: String,
        filter: (LookupElement) -> Boolean,
        waitFor: Duration = 15.seconds,
        action: (List<LookupElement>?) -> Unit
    ) {
        ThreadingAssertions.assertBackgroundThread()
        completionTester.typeWithPauses(string)
        completionTester.joinCommit()
        withContext(Dispatchers.EDT) {
            action(completeBasic(timeout = waitFor, filter = filter))
        }
        completionTester.joinCommit()
    }

    /**
     * Work-around for flaky UI tests. On CI the completion popup sometimes takes a while to show up.
     *
     * Returns null if the single element was auto-completed.
     * Returns an empty list if no lookup appeared in a given [timeout], otherwise returns the list of matching elements.
     */
    private suspend fun completeBasic(
        timeout: Duration = 15.seconds,
        // Wait for the first element that matches this filter to show up
        filter: (LookupElement) -> Boolean = { true },
    ): List<LookupElement>? {
        val start = Instant.now()
        val timeoutMillis = timeout.inWholeMilliseconds

        while (true) {
            val myResult = testFixture.completeBasic()
            if (myResult == null) return null
            val filteredResult = myResult.filter(filter)
            if (filteredResult.isNotEmpty()) return filteredResult
            val passedMillis = Instant.now().toEpochMilli() - start.toEpochMilli()
            if (passedMillis > timeoutMillis) {
                return emptyList()
            }
            LOG.warn("Lookup didn't show up yet, time passed: $passedMillis ms")

            delay(100)
        }
    }

    private fun <T : Any> dumpInlayHints(
        sourceText: String,
        provider: InlayHintsProvider<T>,
        injectionOffset: Int = 0,
        settings: T = provider.createSettings()
    ): String {
        val file = testFixture.file!!
        val editor = testFixture.editor
        val sink = InlayHintsSinkImpl(editor)
        val collector = provider.getCollectorFor(file, editor, settings, sink) ?: error("Collector is expected")
        val collectorWithSettings = CollectorWithSettings(collector, provider.key, file.language, sink)
        collectorWithSettings.collectTraversingAndApply(editor, file, true)
        return InlayDumpUtil.dumpInlays(
            sourceText,
            editor = testFixture.editor,
            filter = { r -> r.widthInPixels > 0 },
            renderer = { renderer, _ ->
                if (renderer !is PresentationRenderer && renderer !is LinearOrderInlayRenderer<*>) error("renderer not supported")
                renderer.toString()
            },
            offsetShift = -injectionOffset
        )
    }

    @RequiresReadLock
    private fun JupyterPsiCell.toInjectedKtFiles(): List<KtFile> {
        val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
        return getInjectedKtFiles(injectedLanguageManager).ifEmpty {
            error("No suitable KtFile found in a host")
        }
    }
}
