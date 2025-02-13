// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service.pass

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.getInjectedKtFiles
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.TreeTraversal
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger


/**
 * doc should be about what and how is tracked, e,g. why we count injections
 */
// TODO: refactor doc
/**
 * injected
 */
internal class InjectedFilesDataTracker
: Disposable  {
    data class InjectedFileData(
        val notebookCellIndex: Int,
        val file: KtFile,
        val ktFileRange: TextRange,
        val injectionHost: PsiLanguageInjectionHost,
        val totalTokens: Int
    ) {
        val processedTokens = AtomicInteger(0)
    }

    val finishedFilesIndexes = ConcurrentCollectionFactory.createConcurrentSet<Int>()
    private val fileToInjectionData = ConcurrentHashMap<KtFile, InjectedFileData>()
    private val unrecognizedFiles = ConcurrentHashMap.newKeySet<PsiFile>()
    @Volatile
    private var targetPsiFile: PsiFile? = null

    val targetIndexes: Set<Int>
        get() = fileToInjectionData.mapTo(mutableSetOf()) {
            it.value.notebookCellIndex
        }

    fun passCreated(targetIndexes: Set<Int>, cells: List<PsiLanguageInjectionHost>?, completeRangeInd: Int?) {
        if (cells == null || cells.isEmpty()) {
            return
        }
        val project = cells.first().project
        val injectedLanguageManager = InjectedLanguageManager.getInstance(project)

        if (targetIndexes.isEmpty() && completeRangeInd != null) {
            val psiCell = cells.getOrNull(completeRangeInd) ?: return
            targetPsiFile = psiCell.getInjectedKtFiles(injectedLanguageManager).firstOrNull()
            return
        }


        for (ind in targetIndexes) {
            val psiCell = cells.getOrNull(ind) ?: continue
            val injectedKtFiles = psiCell.getInjectedKtFiles(injectedLanguageManager)
            // skip non Kt
            if (injectedKtFiles.isEmpty()) {
                finishedFilesIndexes.add(ind)
                continue
            }

            for (ktFile in injectedKtFiles) {
                val ktFileRange = injectedLanguageManager.injectedToHost(ktFile, ktFile.textRange)
                fileToInjectionData[ktFile] = InjectedFileData(
                    ind,
                    ktFile,
                    ktFileRange,
                    psiCell,
                    numberOfNonWhiteSpaceLeaves(ktFile)
                )

                if (ind == completeRangeInd) {
                    targetPsiFile = ktFile
                }
            }
        }
        //sharedLogger.warn("Created pass for $targetIndexes")
        unrecognizedFiles.clear()
    }

    fun finishedForFile(psiFile: PsiFile) {
        val ind = fileToInjectionData[psiFile]?.notebookCellIndex
        if (ind == null) {
            notebookLogger().info("Seen unrecognized file during pass")
            //unrecognizedFiles.add(psiFile)
            return
        }

        finishedFilesIndexes.addIfNotNull(ind)
        notebookLogger().debug("Finished visitors for $ind")

        return
    }


    fun getFileInjectionData(psiFile: PsiFile): InjectedFileData? {
        return fileToInjectionData[psiFile]
    }

    fun determineFilesLeftToHighlight(markupModel: MarkupModelEx, skippedFiles: MutableSet<Int>) {
        val data = fileToInjectionData

        for ((_, fileData) in data) {
            val range = fileData.ktFileRange
            if (range.length == 0) continue

            fileData.processedTokens.set(0)
            val seenHighlighters = mutableSetOf<RangeHighlighter>()

            markupModel.processRangeHighlightersOverlappingWith(range.startOffset, range.endOffset) {
                // injected syntax is greater than regular SYNTAX
                if ((it.layer < INJECTED_SYNTAX_LAYER_BORDER && it.layer != HighlighterLayer.ERROR) && seenHighlighters.add(it)) {
                    fileData.processedTokens.incrementAndGet()
                }
                true
            }
            val tokens = seenHighlighters.size

            if (tokens < fileData.totalTokens - 1) {
                skippedFiles.add(fileData.notebookCellIndex)
            }
        }
    }


    fun processUnrecognizedFiles(topLevelFile: PsiFile?, highlightingQueue: MutableSet<Int>?) {
        val unrecognizedFiles = unrecognizedFiles

        if (unrecognizedFiles.isNotEmpty()) {
            val project = unrecognizedFiles.first().project
            val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
            val unseenFiles = ReadAction.compute<List<Int>, Throwable> {
                unrecognizedFiles.toCellsIndexes(topLevelFile, injectedLanguageManager)
            }

            highlightingQueue?.addAll(
                unseenFiles
            )
            unrecognizedFiles.clear()
        }
    }

    fun isFileTarget(file: PsiFile): Boolean {
        return file == targetPsiFile
    }

    private fun Collection<PsiFile>.toCellsIndexes(jupyterPsiFile: PsiFile?, manager: InjectedLanguageManager): List<Int> {
        val cells = jupyterPsiFile.getNotebookCells()
        return mapNotNull { injected -> cells.indexOf(manager.getInjectionHost(injected)) }
    }

    /**
     * Resets operational data before new HL pass.
     * The state should be cleared out.
     */
    fun clear() {
        unrecognizedFiles.clear()
        targetPsiFile = null
        fileToInjectionData.clear()
        finishedFilesIndexes.clear()
    }

    override fun dispose() {
        clear()
    }

    companion object {
        internal const val INJECTED_SYNTAX_LAYER_BORDER = HighlighterLayer.CARET_ROW - 1
    }
}


internal fun numberOfNonWhiteSpaceLeaves(ktFile: KtFile): Int {
    return SyntaxTraverser.psiTraverser(ktFile)
        .traverse(TreeTraversal.LEAVES_DFS)
        .count { psiLeaf ->
            PsiUtilCore.getElementType(psiLeaf) != TokenType.WHITE_SPACE
                    && psiLeaf !is KtPackageDirective
        }
}