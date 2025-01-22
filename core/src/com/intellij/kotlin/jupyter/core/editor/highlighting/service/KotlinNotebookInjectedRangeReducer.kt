// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service

import com.intellij.codeInsight.daemon.impl.InjectedLanguageHighlightingRangeReducer
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile.Companion.takeIfBacked
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.getCellRangesInDocumentOrNull
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.getCell
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterFile

internal class KotlinNotebookInjectedRangeReducer : InjectedLanguageHighlightingRangeReducer {

    override fun reduceRange(file: PsiFile, editor: Editor): List<TextRange>? {
        val jupyterFile = file as? JupyterFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(jupyterFile.virtualFile) ?: return null
        if (!NotebookCellLines.hasSupport(editor)) return null

        val backedNotebook = takeIfBacked(jupyterFile.virtualFile)

        val cells = try {
            file.getNotebookCells()
        } catch (ex: IllegalStateException) {
            LOG.debug("Can't get Notebook cells list: $ex")
            null
        }
        val project = file.project
        val caretOffSet = editor.caretModel.offset
        val cellUnderEditor = editor.getCell(document.getLineNumber(caretOffSet))
        ProgressManager.checkCanceled()

        cells?.ensureScriptConfigurations(
            InjectedLanguageManager.getInstance(project))
        val highlightingManager = backedNotebook?.let { NotebookHighlightingService.getForFile(project, it) }
        val dataController = highlightingManager?.dataController

        return synchronized(document) {
            val cellIndx = dataController?.notebookChangedCellIndex
            val cellOfChange = cellIndx?.let { // notebook file is already rebuild
                cells?.getOrNull(it)
            }
            val cellChangeRange = cellOfChange?.textRange
            val completeHLRange = dataController?.completeHighlightingRange
            var severalUpdates = dataController?.notebookDocumentTargetRanges
            val highlightingQueue = dataController?.notebookRangesQueuedForHL
            backedNotebook?.let {
                highlightingQueue?.addAll(dataController.lastExecutedCellsBatch)
            }

            if (cellChangeRange != null && (completeHLRange == null || completeHLRange.startOffset == cellChangeRange.startOffset)) { // converge
                val correctUnderEditorInd = cellUnderEditor.ordinal
                // this might happen after redo action
                val nothingMatches =
                    completeHLRange == null && (correctUnderEditorInd - cellIndx > 0) && severalUpdates == null
                if (nothingMatches) {
                    val toPut = cells?.get(correctUnderEditorInd)?.textRange
                    val structureChangeIndicator =
                        dataController.notebookDocumentStructureNontrivialChanged
                    dataController.update {
                        completeHighlightingRange = toPut
                        notebookChangedCellIndex = correctUnderEditorInd
                    }
                    // clear only if nothing structural was done
                    if (structureChangeIndicator.get() == false) {
                        highlightingQueue?.clear()
                    }
                    highlightingQueue?.addIfNotNull(correctUnderEditorInd)
                    highlightingManager.passCreated(
                        highlightingQueue ?: setOf(correctUnderEditorInd),
                        cells,
                        correctUnderEditorInd
                    )
                    return listOfNotNull(toPut)
                }
                dataController.update {
                    completeHighlightingRange = cellChangeRange
                    if (severalUpdates?.size == 1) {
                        highlightingQueue?.add(cellIndx)
                        notebookDocumentTargetRanges = highlightingQueue
                    }
                }
            }
            if (cellIndx != null && dataController.completeHighlightingRange != null) {
                val newCompleteRange = if (cellUnderEditor.ordinal != cellIndx) {
                    highlightingQueue?.add(cellIndx)
                    cells?.get(cellUnderEditor.ordinal)?.textRange
                } else cellChangeRange
                dataController.update {
                    completeHighlightingRange = newCompleteRange
                }
            }
            if (cellIndx == null && severalUpdates?.size == 1) { // converge
                if (severalUpdates.first() != 0) {
                    severalUpdates = setOfNotNull(cellUnderEditor.ordinal).union(severalUpdates).toMutableSet()
                    dataController?.update {
                        completeHighlightingRange = cells?.get(cellUnderEditor.ordinal)?.textRange
                        notebookDocumentTargetRanges = severalUpdates
                    }
                } else if (highlightingQueue?.size == 0) return null
            }

            if (highlightingQueue != null && cells != null) {
                highlightingQueue.addIfNotNull(cellIndx)
                if (severalUpdates == null) {
                    highlightingManager.passCreated(highlightingQueue, cells, cellUnderEditor.ordinal)
                    return getCellRangesInDocumentOrNull(cells, highlightingQueue)
                }
                highlightingQueue.addAll(severalUpdates)
            }

            val afterRenaming = dataController?.renamingRanges
            if (afterRenaming?.isNotEmpty() == true) {
                return afterRenaming.toList()
            }

            if (severalUpdates?.isNotEmpty() == true && cells != null) { // updates U queue
                val mergedUpdates = severalUpdates.toMutableSet().also {
                    it.addIfNotNull(cellIndx)
                    if (highlightingQueue != null) {
                        it.addAll(highlightingQueue)
                    }
                    highlightingQueue?.addAll(it)
                }
                dataController?.update {
                    notebookDocumentTargetRanges = mergedUpdates
                }
                if (cellIndx == null && dataController?.completeHighlightingRange == null && cellUnderEditor.ordinal != 0) {
                    highlightingQueue?.add(cellUnderEditor.ordinal)
                    dataController?.update {
                        completeHighlightingRange = cells[cellUnderEditor.ordinal]?.textRange
                    }
                }
                highlightingQueue?.add(cellUnderEditor.ordinal)
                //LOG.warn("Run on ind: ${mergedUpdates}, targetIndKey: $cellIndx, underCaret: ${cellUnderEditor}")
                highlightingManager?.passCreated(
                    highlightingQueue ?: setOf(cellUnderEditor.ordinal),
                    cells,
                    cellUnderEditor.ordinal
                )
                return getCellRangesInDocumentOrNull(cells, highlightingQueue)
            }

            dataController?.completeHighlightingRange
        }?.let {
            listOf(it)
        }
    }

    private fun Collection<PsiLanguageInjectionHost>?.ensureScriptConfigurations(manager: InjectedLanguageManager) {
        this?.forEach {
            manager.getInjectedPsiFiles(it)?.firstOrNull { f -> f.first is KtFile }?.first?.let { ktFile ->
                if (ktFile is KtFile) {
                    JupyterCompilerService.getInstance(ktFile.project).ensureScriptConfiguration(ktFile.project, ktFile)
                }
            }
        }
    }

    companion object {
        private val LOG = notebookLogger()
    }
}