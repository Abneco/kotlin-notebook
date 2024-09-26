// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight.hints

import com.intellij.codeInsight.hints.CollectorWithSettings
import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSinkImpl
import com.intellij.codeInsight.hints.LinearOrderInlayRenderer
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingService
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.test.baseTestDataPath
import com.intellij.kotlin.jupyter.test.getCells
import com.intellij.kotlin.jupyter.test.isInjectedKtFile
import com.intellij.kotlin.jupyter.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.containers.isEmpty
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell


abstract class AbstractNotebookTypeHintsBaseTest : KotlinNotebookExecutionBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile
    override fun getTestDataPath() = "$baseTestDataPath/notebooks/codeinsight/hints"
    abstract val provider: InlayHintsProvider<*>

    companion object {
        const val hintsEmptyMessage = "// NO HINTS"
    }


    enum class HintsOccurence {
        PRESENT,
        ABSENT
    }

    open val isLimitTypeHintsByActiveCell: Boolean = false

    @JvmOverloads
    fun <T : Any> runTestProvider(injectionOffset: Int,
                                  injectedFileContents: String,
                                  expectedText: String,
                                  provider: InlayHintsProvider<T>,
                                  settings: T = provider.createSettings(),
                                  verifyHintPresence: Boolean = false) {
        val sourceText = InlayDumpUtil.removeHints(expectedText)
        val actualText = runReadAction {
            dumpInlayHints(sourceText, provider, injectionOffset, settings)
        }
        assertEquals(expectedText, actualText)
        assertEquals(sourceText.trimEnd(), injectedFileContents.trimEnd())

        if(verifyHintPresence) {
            val expectedHintPresence = if (expectedText.lineSequence().any { it.startsWith(hintsEmptyMessage) }) HintsOccurence.ABSENT else HintsOccurence.PRESENT
            val actualHintPresence = if (InlayDumpUtil.inlayPattern.matcher(expectedText).results().isEmpty()) HintsOccurence.ABSENT else HintsOccurence.PRESENT
            assertEquals("Hint presence should match the use of the $hintsEmptyMessage directive.", expectedHintPresence, actualHintPresence)
        }
    }


    protected fun <T : Any> dumpInlayHints(sourceText: String,
                                           provider: InlayHintsProvider<T>,
                                           injectionOffset: Int = 0,
                                           settings: T = provider.createSettings()): String {
        val file = myFixture.file!!
        val editor = myFixture.editor
        val sink = InlayHintsSinkImpl(editor)
        val collector = provider.getCollectorFor(file, editor, settings, sink) ?: error("Collector is expected")
        val collectorWithSettings = CollectorWithSettings(collector, provider.key, file.language, sink)
        collectorWithSettings.collectTraversingAndApply(editor, file, true)
        return InlayDumpUtil.dumpHintsInternal(
            sourceText,
            editor = myFixture.editor,
            filter = { r -> r.widthInPixels > 0 },
            renderer = { renderer, _, _ ->
                if (renderer !is PresentationRenderer && renderer !is LinearOrderInlayRenderer<*>) error("renderer not supported")
                renderer.toString()
            },
            offsetShift = -injectionOffset
        )
    }

    protected fun enableLimitByActiveCell() {
        KotlinNotebookProjectOptionsProvider.getInstance(project).state.shouldLimitTypeHintsByActiveCell = true
    }

    protected fun disableLimitByActiveCell() {
        KotlinNotebookProjectOptionsProvider.getInstance(project).state.shouldLimitTypeHintsByActiveCell = false
    }


    protected fun collectTraversingForCellOrAny(collector: InlayHintsCollector, sink: InlayHintsSinkImpl, editor: Editor, file: PsiFile, cell: JupyterPsiCell? = null) {
        val traverser = SyntaxTraverser.psiTraverser(file)
        if (cell == null) {
            traverser.forEach {
                collector.collect(it, editor, sink)
            }
        } else {
            collector.collect(cell, editor, sink)
        }
    }


    protected fun <T: Any> doTest(provider: InlayHintsProvider<T>, cellInd: Int, limitedAreaTargetInd: Int? = null,
                                  setupAction: (T) -> Unit = {}) = runInEdtAndWait {
        val notebookFile = configureExecutionTest()
        val cells = notebookFile.getCells()
        val neededCell = cells.getOrNull(cellInd) ?: error("Invalid cell index provided")
        val backedNotebook = BackedNotebookVirtualFile.takeIfBacked(originalVirtualFile)
            ?: error("Couldn't find BackedNotebookFile for $originalVirtualFile")
        val hlManager = NotebookHighlightingService.getForFile(project, backedNotebook)
        limitedAreaTargetInd?.let {
            enableLimitByActiveCell()
            val completeAnalysis = cells.getOrNull(limitedAreaTargetInd) ?: error("Provided complete highlighting area is invalid")
            hlManager.dataController.update {
                completeHighlightingRange = completeAnalysis.textRange
            }
        }
        val injectedLanguageManager = InjectedLanguageManager.getInstance(notebookFile.project)

        val injectedFile = runReadAction {
            (injectedLanguageManager
                .getInjectedPsiFiles(neededCell)?.firstOrNull { it.first.containingFile.isInjectedKtFile() }?.first as? PsiFile)
        } ?: error("No suitable KtFile found in a host")

        val fileOffset = injectedLanguageManager.injectedToHost(injectedFile, 0)

        with(provider) {
            val expectedFileContents = FileUtil.loadFile(getTestFile(".kt"), true)
            val settings = createSettings()
            setupAction(settings)
            runTestProvider(fileOffset, injectedFile.text, expectedFileContents, this, settings, verifyHintPresence = true)
        }

        limitedAreaTargetInd?.let {
            disableLimitByActiveCell()
        }
    }
}