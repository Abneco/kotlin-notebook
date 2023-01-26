// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.highlighting

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.ArrayUtilRt
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.KotlinNotebookExecutionBaseTestCase

abstract class AbstractNotebookHighlightingTest : KotlinNotebookExecutionBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile
    override fun getTestDataPath() = "$baseTestDataPath/notebooks/highlighting"

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

    protected fun getDocument(file: PsiFile): Document? {
        return PsiDocumentManager.getInstance(file.project).getDocument(file)
    }

    protected fun removeDuplicatedRangesForInjected(infos: MutableList<out HighlightInfo>) {
        infos.sortedWith { o1: HighlightInfo, o2: HighlightInfo ->
            val i = o1.startOffset - o2.startOffset
            if (i != 0) i else o1.severity.myVal - o2.severity.myVal
        }
        var prevInfo: HighlightInfo? = null
        val it = infos.iterator()
        while (it.hasNext()) {
            val info = it.next()
            if (prevInfo != null && info.severity === HighlightInfoType.SYMBOL_TYPE_SEVERITY && info.description == null && info.startOffset == prevInfo.startOffset && info.endOffset == prevInfo.endOffset) {
                it.remove()
            }
            prevInfo = if (info.type === HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT) info else null
        }
    }

    protected fun doTest(strategy: ResultCheckStrategy, notebookAftermathAction: (PsiFile) -> Unit = {}) {
        val notebookFile = configureExecutionTest()
        InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(notebookFile)
        val filter = createFilterForStrategy(strategy)
        val expectedData = getExpectedHighlightingData(true, false, true)
        val results = doHighlighting()
        assert(results.none { it.description != null && it.description == scriptingMissingClassError })

        val isHasShadowed = results.any { it.description != null && (it.description.startsWith("Not yet provided symbol") || it.description.startsWith("Improper usage")) }
        if (strategy.isOnlyValidSyntax()) {
            assert(results.none { it.severity == HighlightSeverity.ERROR })
        }
        if (strategy.isShadowedErrors()) {
            assert(isHasShadowed)
            assert(results.none { it.severity == HighlightSeverity.ERROR })
        }

        if (strategy.isOnlyValidSyntax()) {
            assert(results.none { it.severity == HighlightSeverity.ERROR })
            assert(!isHasShadowed)
        }
        val actualData = results.filter { filter(it) }
        if (strategy == ResultCheckStrategy.OnlyValidSyntax || strategy == ResultCheckStrategy.ShadowedErrors) {
            expectedData.checkResult(notebookFile, actualData, myFixture.editor.document.text)
        }
        if (strategy == ResultCheckStrategy.WithErrors) {
            assert(results.any { it.severity == HighlightSeverity.ERROR })
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
            toIgnoreList.add(Pass.WHOLE_FILE_LOCAL_INSPECTIONS)
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

    protected fun createFilterForStrategy(strategy: ResultCheckStrategy): (HighlightInfo) -> Boolean {
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
