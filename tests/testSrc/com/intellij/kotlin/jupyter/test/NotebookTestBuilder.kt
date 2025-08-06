// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.injected.editor.EditorWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.notebook.JupyterRuntimeService
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessages
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessagesTester
import com.intellij.kotlin.jupyter.test.notebook.execution.buildJacksonObject
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.startOffset
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ArrayUtilRt
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.core.moveCaret

/**
 * Strategy used when checking highlight information
 */
enum class HighlightCheckStrategy {
    OnlyValidSyntax, // No errors should be found in the highlighting info
    WithErrors, // Errors should be found in the highlighting info
    ShadowedErrors // There should be errors in cells not in focus, but the cell in focus has valid syntax.
}

/**
 * Class providing a simpler API for Kotlin Notebook tests, making it possible to define the test flow as
 * seen from a user perspective.
 *
 * In the initial state of the notebook. The caret is placed at index 0 in the first cell (index 0).
 */
class NotebookTestBuilder(
    private val project: Project,
    private val notebookFile: PsiFile,
    private val testFixture: CodeInsightTestFixture,
    private val testCase: KotlinNotebookTestCase,
) {

    // Returns the number of cells (of all types) in the notebook
    val cellCount: Int
        get() { return notebookFile.getCells().count() }

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

    // State required to track if a Jupyter Session has been started
    private var jupyterSessionStarted: Boolean = false
    private var notebookBackedFile: BackedNotebookVirtualFile? = null
    private var jupyterSession: JupyterNotebookSession? = null
    private val jupyterSessionSetup = {
        val project = notebookFile.project
        notebookBackedFile = notebookFile.virtualFile.toKotlinNotebookBackedFile()!!
        jupyterSession = runBlocking {
            JupyterRuntimeService.getInstance(project).getOrCreateSession(notebookBackedFile!!)!!
        }
    }
    private val jupyterSessionCleanup = {
        runBlocking {
            jupyterSession?.deleteSession()
        }
        // make sure to drop previous data
        JupyterCompilerService.getInstance(project).removeSession(notebookBackedFile!!)
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
     * @param cellIndex which cell to execute. Starting cell is 0.
     * @param waitForDependencies if `true`, the method will block until the IDE classpath as been
     * updated with the newly compiled snippet as well as any potential new dependencies. Default
     * is `false` to match IDE behavior.
     * @return output as a JSON object. If the cell generates no output, an empty object is returned.
     */
    fun executeCell(
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
                tester = object: ReceivedMessagesTester {
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
    fun moveCaretToCell(cellIndex: Int, tokenOffset: Int = 0) {
        runInEdt {
            val nextCell = notebookFile.getCells()[cellIndex]
            testFixture.editor.moveCaret(nextCell.startOffset + tokenOffset)
        }
    }

    /**
     * Run highlighting on the notebook and return the result of it.
     */
    fun runHighlighting(): HighlightingResult {
        if (!highlighterDaemonStarted) {
            highlightSetup()
        }
        val results = runInEdtAndGet {
            doHighlighting()
        }
        return HighlightingResult(notebookFile, testFixture, results)
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
}
