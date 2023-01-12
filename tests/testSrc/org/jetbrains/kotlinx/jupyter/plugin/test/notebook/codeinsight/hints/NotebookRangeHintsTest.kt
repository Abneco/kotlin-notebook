// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.codeinsight.hints


import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlinx.jupyter.plugin.codeinsight.NotebookValuesHintProvider
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.markHostAsCompleteAnalysisTarget
import org.jetbrains.kotlinx.jupyter.plugin.test.getCells
import org.jetbrains.kotlinx.jupyter.plugin.test.isInjectedKtFile
import org.junit.Test
import java.io.File

class NotebookRangeHintsTest : AbstractNotebookTypeHintsBaseTest() {
    override val isLimitTypeHintsByActiveCell: Boolean = false
    override fun getTestDataPath() = "${super.getTestDataPath()}/ranges"

    @Test
    fun testSimpleRanges() {
        doTest(0)
    }

    @Test
    fun testLimitedRanges() {
        doTest(1, 1)
    }

    private fun doTest(cellInd: Int, limitedAreaTargetInd: Int? = null) {
        val notebookFile = configureExecutionTest()
        val cells = notebookFile.getCells()
        val neededCell = cells.getOrNull(cellInd) ?: error("Invalid cell index provided")
        limitedAreaTargetInd?.let {
            enableLimitByActiveCell()
            val completeAnalysis = cells.getOrNull(limitedAreaTargetInd) ?: error("Provided complete highlighting area is invalid")
            val doc = myFixture.getDocument(notebookFile) ?: error("Document should not be null")
            markHostAsCompleteAnalysisTarget(doc, completeAnalysis)
        }

        val cellShift = neededCell.startOffset
        val injectedFileContents = runReadAction {
            (InjectedLanguageManager.getInstance(project)
                .getInjectedPsiFiles(neededCell)?.firstOrNull { it.first.containingFile.isInjectedKtFile() }?.first as? PsiFile)?.text
        } ?: error("No suitable KtFile found in a host")

        with(NotebookValuesHintProvider()) {
            val expectedFileContents = FileUtil.loadFile(File("$testDataPath/${getTestName(true)}.kt"), true)
            val settings = createSettings()
            runTestProvider(cellShift, injectedFileContents, expectedFileContents, this, settings, verifyHintPresence = true)
        }

        limitedAreaTargetInd?.let {
            disableLimitByActiveCell()
        }
    }
}