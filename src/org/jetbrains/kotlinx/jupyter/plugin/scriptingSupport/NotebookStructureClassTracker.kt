// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.plugin.debug.util.ExecutedPresentCellInfo
import org.jetbrains.kotlinx.jupyter.plugin.editor.find.NotebookReferenceFinder
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.NotebookChangeEventsType
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.NotebookMoveEvent
import org.jetbrains.kotlinx.jupyter.plugin.util.toPsiFile
import org.jetbrains.kotlinx.jupyter.plugin.util.withReadAccess
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterNotebook
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell
import kotlin.math.abs

/**
 * Keeps information about what classes were compiled in the current Notebook.
 * Information is provided with accordance to the cell index in the Notebook structure.
 */
internal interface NotebookClassesInCellsInfoHandler {
    val nextCompiledClassLineIndex: Int

    val cellOrdinalToClassNameStructure: MutableMap<Int, Set<String>>
    val classNameToCellOrdinalStructure: MutableMap<String, Int>

    fun updateCellInformationBeforeExecution(cell: JupyterPsiCell, ordinal: Int?)

    fun storeCompliedDataInCell(snippetMetadata: EvaluatedSnippetMetadata, psiCell: JupyterPsiCell)

    fun changeCellsData(effectedIndexes: Collection<Int>,
                                 eventType: NotebookChangeEventsType,
                                 moveEvent: NotebookMoveEvent? = null,
                                 invokedMoveEventInInd: Int? = null)

    // Handlers for events
    fun notebookDataCleared()
}



class NotebookStructureClassTracker(
    private val project: Project,
    private val file: BackedNotebookVirtualFile,
    private val coroutineScope: CoroutineScope,
    parentDisposable: Disposable
): NotebookClassesInCellsInfoHandler, Disposable {
    init {
      Disposer.register(parentDisposable, this)
    }
    private val psiFile = withReadAccess {
        file.file.toPsiFile(project)
    }
    private val knownCellInfo = ExecutedPresentCellInfo(psiFile)

    override val cellOrdinalToClassNameStructure: MutableMap<Int, Set<String>>
        get() = knownCellInfo.cellOrdinalToClassName

    override val classNameToCellOrdinalStructure: MutableMap<String, Int>
        get() = knownCellInfo.classNameToCellOrdinal

    override val nextCompiledClassLineIndex: Int
        get() {
            val cellsCounter = JupyterCompilerService.getForFile(project, file).executedCellsCount - 1
            return  if (cellsCounter == -1) 1 else cellsCounter + 1
        }

    override fun storeCompliedDataInCell(snippetMetadata: EvaluatedSnippetMetadata, psiCell: JupyterPsiCell) {
        fun storeReferenceInfo(compiledClassName: MutableSet<String>, cellInd: Int?) {
            NotebookHighlightingService.getForFile(project, file)
                .dataController.invalidateStateAfterCellExecution(executedCellInd = cellInd)
            synchronized(psiCell) {
                val last = psiCell.getUserData(NotebookReferenceFinder.CELL_CLASS_NAME)?.firstOrNull()
                compiledClassName.addIfNotNull(last)
                psiCell.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, compiledClassName)
            }
        }
        val injectManager = InjectedLanguageManager.getInstance(project)
        val classNamesToCellOrdinal = classNameToCellOrdinalStructure

        val compiledClassName = snippetMetadata.compiledData.sources.mapTo(mutableSetOf()) {
            it.fileName.substringBefore(".kts").let { f -> f + "_jupyter" }
        }
        var nextCellInd: Int? = null
        (psiCell.parent as? JupyterNotebook)?.psiCellList?.let { cells ->
            val executedCellInd = cells.indexOf(psiCell)
            if (executedCellInd != -1) {
                cellOrdinalToClassNameStructure[executedCellInd] = compiledClassName
                compiledClassName.forEach { classNamesToCellOrdinal[it] = executedCellInd }
                nextCellInd = if (executedCellInd + 1 != cells.size) executedCellInd + 1 else null
            }
        }
        try {
            (injectManager.getInjectedPsiFiles(psiCell)?.firstOrNull()?.first as? PsiFile)
                ?.putUserData(NotebookReferenceFinder.CELL_CLASS_NAME, compiledClassName)
        } catch (ex: Exception) {
            if (ex is ProcessCanceledException) {
                coroutineScope.async {
                    storeReferenceInfo(compiledClassName, nextCellInd)
                }
                return
            } else LOG.warn("Exception during storing cell-related data", ex)
        }
        storeReferenceInfo(compiledClassName, nextCellInd)
    }

    override fun changeCellsData(
        effectedIndexes: Collection<Int>,
        eventType: NotebookChangeEventsType,
        moveEvent: NotebookMoveEvent?,
        invokedMoveEventInInd: Int?) {
        if (effectedIndexes.isEmpty()
            || eventType != NotebookChangeEventsType.CELL_ADD && eventType != NotebookChangeEventsType.CELL_DELETE) return
        val presentRecords = cellOrdinalToClassNameStructure.filterKeys { it in effectedIndexes || it == invokedMoveEventInInd }.ifEmpty { return }
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
            val storedData = cellOrdinalToClassNameStructure[invokedInCell]
            val isCellUp = it == NotebookMoveEvent.CELL_UP
            val anotherAffectedInd = if (isCellUp) invokedInCell - 1 else invokedInCell + 1
            // skip if it will be processed later
            separatedByGaps.firstOrNull { set -> invokedInCell in set || anotherAffectedInd in set }?.let { foundContainer ->
                foundContainer.removeIf { elem -> elem == invokedInCell || elem == anotherAffectedInd }
            }

            val storedInAnother = cellOrdinalToClassNameStructure[anotherAffectedInd]
            if (storedData != null) {
                cellOrdinalToClassNameStructure[anotherAffectedInd] = storedData
            } else cellOrdinalToClassNameStructure.remove(anotherAffectedInd)
            if (storedInAnother != null) {
                cellOrdinalToClassNameStructure[invokedInCell] = storedInAnother
            } else cellOrdinalToClassNameStructure.remove(invokedInCell)
        }


        val toRemove = mutableSetOf<Int>()
        for (consecutiveData in separatedByGaps) {
            consecutiveData.forEach { ind ->
                val data = presentRecords[ind] ?: return@forEach
                val newInd = ind + indexShift
                if (newInd >= 0) {
                    cellOrdinalToClassNameStructure[newInd] = data
                }
            }
            toRemove.addIfNotNull(consecutiveData.lastOrNull())
        }

        toRemove.forEach { cellOrdinalToClassNameStructure.remove(it) }
        knownCellInfo.structureChanged()
    }

    override fun updateCellInformationBeforeExecution(cell: JupyterPsiCell, ordinal: Int?) {
        knownCellInfo.updateInfoBeforeCellExecution(
            cell,
            ordinal,
            nextCompiledClassLineIndex
        )
    }

    override fun notebookDataCleared() {
        knownCellInfo.clear()
    }

    override fun dispose() {
        notebookDataCleared()
    }

    companion object {
        private val LOG = thisLogger()
    }
}