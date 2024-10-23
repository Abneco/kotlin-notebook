// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.highlighting

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.injected.editor.EditorWindow
import com.intellij.jupyter.core.jupyter.nbformat.CELL_MARKER
import com.intellij.jupyter.core.jupyter.nbformat.MARKDOWN_CELL_SUFFIX
import com.intellij.kotlin.jupyter.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import com.intellij.kotlin.jupyter.test.setUpScriptingDependencies
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ArrayUtilRt
import org.jetbrains.plugins.notebooks.tests.withSwingMarkdownRenderMode

abstract class AbstractNotebookHighlightingTest : KotlinNotebookExecutionBaseTestCase("notebooks/highlighting") {
    abstract val canChangeDocumentDuringHighlighting: Boolean
    open val shouldDoInspections: Boolean = true
    open val shouldDoFolding: Boolean = true

    private companion object {
        const val scriptingMissingClassError = "MISSING_SCRIPT_RECEIVER_CLASS"

        fun ResultCheckStrategy.isOnlyValidSyntax(): Boolean = this == ResultCheckStrategy.OnlyValidSyntax
        fun ResultCheckStrategy.isWithErrors(): Boolean = this == ResultCheckStrategy.WithErrors
        fun ResultCheckStrategy.isShadowedErrors(): Boolean = this == ResultCheckStrategy.ShadowedErrors
    }

    enum class ResultCheckStrategy {
        OnlyValidSyntax, // means no errors
        WithErrors,
        ShadowedErrors
    }


    override fun setUp() {
        super.setUp()
        (DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl).prepareForTest()
        DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled = false
    }

    override fun tearDown() {
        try {
            DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled = true
            val daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl
            daemonCodeAnalyzer.cleanupAfterTest()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    protected open fun getExpectedHighlightingData(
        checkWarnings: Boolean,
        checkWeakWarnings: Boolean,
        checkInfos: Boolean
    ): ExpectedHighlightingData {
        return ExpectedHighlightingData(myFixture.editor.document, checkWarnings, checkWeakWarnings, checkInfos)
    }

    protected fun doTest(strategy: ResultCheckStrategy, notebookAftermathAction: (PsiFile) -> Unit = {}) {
        withSwingMarkdownRenderMode {
            doTestImpl(strategy, notebookAftermathAction)
        }
    }

    private fun doTestImpl(
        strategy: ResultCheckStrategy,
        notebookAftermathAction: (PsiFile) -> Unit
    ) {
        val notebookFile = configureExecutionTest()
        setUpScriptingDependencies(myFixture)
        val filter = createFilterForStrategy(strategy)
        val expectedData = getExpectedHighlightingData(true, false, true)
        val results = runInEdtAndGet {
            doHighlighting()
        }
        assertTrue(results.none { it.description != null && it.description == scriptingMissingClassError })

        val isHasShadowed =
            results.any { it.description != null && (it.description.startsWith("Not yet provided symbol") || it.description.startsWith("Improper usage")) }
        assertTrue(results.none { it.text.contains(CELL_MARKER) || it.text.contains("$CELL_MARKER $MARKDOWN_CELL_SUFFIX") })

        if (strategy.isOnlyValidSyntax()) {
            assertTrue(results.none { it.severity == HighlightSeverity.ERROR })
            assertTrue(!isHasShadowed)
        }
        if (strategy.isShadowedErrors()) {
            assertTrue(isHasShadowed)
            assertTrue(results.none { it.severity == HighlightSeverity.ERROR })
        }

        val actualData = results.filter { filter(it) }
        if (strategy == ResultCheckStrategy.OnlyValidSyntax || strategy == ResultCheckStrategy.ShadowedErrors) {
            expectedData.checkResult(notebookFile, actualData, myFixture.editor.document.text)
        }
        if (strategy == ResultCheckStrategy.WithErrors) {
            assertTrue(results.any { it.severity == HighlightSeverity.ERROR })
        }

        notebookAftermathAction(notebookFile)
    }

    protected open fun doHighlighting(): List<HighlightInfo> {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val toIgnoreList = mutableListOf<Int>()
        if (shouldDoFolding) {
            toIgnoreList.add(Pass.UPDATE_FOLDING)
        }
        if (shouldDoInspections) {
            toIgnoreList.add(Pass.LOCAL_INSPECTIONS)
        }
        val toIgnore = if (toIgnoreList.isEmpty()) ArrayUtilRt.EMPTY_INT_ARRAY else toIgnoreList.toIntArray()
        var editor = myFixture.editor
        var file = myFixture.file
        if (editor is EditorWindow) {
            editor = editor.delegate
            file = InjectedLanguageManager.getInstance(file.project).getTopLevelFile(file)
        }
        return CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, toIgnore, canChangeDocumentDuringHighlighting)
    }

    private fun createFilterForStrategy(strategy: ResultCheckStrategy): (HighlightInfo) -> Boolean {
        return when (strategy) {
            ResultCheckStrategy.ShadowedErrors -> { {
                it.severity.displayName == "INJECTED_FRAGMENT_SYNTAX" || it.severity.displayName == "ERROR"
            } }
            ResultCheckStrategy.OnlyValidSyntax -> { {
                it.severity.displayName == "INJECTED_FRAGMENT_SYNTAX"
            } }
            ResultCheckStrategy.WithErrors -> { {
                it.severity.displayName == "ERROR"
            } }
        }
    }
}
