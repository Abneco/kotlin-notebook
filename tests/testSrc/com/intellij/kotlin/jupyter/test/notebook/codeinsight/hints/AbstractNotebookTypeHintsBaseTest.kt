// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight.hints

import com.intellij.codeInsight.hints.CollectorWithSettings
import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSinkImpl
import com.intellij.codeInsight.hints.LinearOrderInlayRenderer
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingService
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.util.getInjectedKtFiles
import com.intellij.kotlin.jupyter.test.getCells
import com.intellij.kotlin.jupyter.test.kotlinNotebookFile
import com.intellij.kotlin.jupyter.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.isEmpty
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell


abstract class AbstractNotebookTypeHintsBaseTest(testDataPath: String) : KotlinNotebookExecutionBaseTestCase("notebooks/codeinsight/hints/$testDataPath") {
    override lateinit var originalVirtualFile: VirtualFile
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
    @RequiresEdt
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

    @RequiresReadLock
    protected fun JupyterPsiCell.toInjectedKtFiles(): List<KtFile> {
        val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
        return getInjectedKtFiles(injectedLanguageManager).ifEmpty {
            error("No suitable KtFile found in a host")
        }
    }

    protected fun <T: Any> doTest(provider: InlayHintsProvider<T>, cellInd: Int, limitedAreaTargetInd: Int? = null,
                                  setupAction: (T) -> Unit = {}) {
        val notebookFile = configureExecutionTest()
        val cells = notebookFile.getCells()
        val neededCell = cells.getOrNull(cellInd) ?: error("Invalid cell index provided")
        setCompleteAnalysisArea(neededCell)
        val ktFile = ReadAction.compute<KtFile, Throwable> {
            neededCell.toInjectedKtFiles().first()
        }

        val fileOffset = InjectedLanguageManager.getInstance(project).injectedToHost(ktFile, 0)

        doTestWithJupyterSessionAndBaseDependencies(notebookFile) {
            provider.doProviderTest(
                ktFile,
                fileOffset,
                setupAction
            )
        }

        limitedAreaTargetInd?.let {
            disableLimitByActiveCell()
        }
    }

    protected fun setCompleteAnalysisArea(targetCellInd: JupyterPsiCell?) {
        if (targetCellInd == null) {
            return
        }
        val backedNotebook = myFixture.kotlinNotebookFile
            ?: error("Couldn't find BackedNotebookFile for $originalVirtualFile")
        val hlManager = NotebookHighlightingService.getForFile(project, backedNotebook)

        enableLimitByActiveCell()
        val cellRange = ReadAction.compute<TextRange, Exception> {
            targetCellInd.textRange
        }
        hlManager.dataController.update {
            completeHighlightingRange = cellRange
        }
    }

    private inline fun <T: Any> InlayHintsProvider<T>.doProviderTest(
        injectedFile: KtFile,
        fileOffset: Int,
        crossinline setupAction: (T) -> Unit,
    ) {
        with(this) {
            val expectedFileContents = FileUtil.loadFile(getTestFile(".kt"), true)
            val settings = createSettings()
            setupAction(settings)

            invokeAndWaitIfNeeded {
                runTestProvider(fileOffset, injectedFile.text, expectedFileContents, this, settings, verifyHintPresence = true)
            }
        }
    }
}