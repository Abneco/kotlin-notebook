// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.codeInsight.daemon.impl.InjectedLanguageHighlightingRangeReducer
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util.NotebookHighlightingUtilityObject.NOTEBOOK_INJECTED_FILE_EXTENSION
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util.NotebookHighlightingUtilityObject.NonTargetHostErrorMark
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util.NotebookHighlightingUtilityObject.SCRIPTING_MISSING_BASE_CLASS_ERROR
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util.NotebookHighlightingUtilityObject.SCRIPTING_MISSING_DEPENDENCY_PREFIX
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util.getCellRangesInDocumentOrNull
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util.looksLikeNotebookFile
import org.jetbrains.kotlinx.jupyter.plugin.editor.notifications.NotebookNotificationUtility
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.NotebookCodeSnippetsChangeListener
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookCells
import org.jetbrains.kotlinx.jupyter.plugin.util.reportErrorTestAware
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile.Companion.takeIfBacked
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.getCell


internal class KotlinNotebookInjectedRangeReducer : InjectedLanguageHighlightingRangeReducer {

    override fun reduceRange(file: PsiFile, editor: Editor): List<TextRange>? {
        if (!file.looksLikeNotebookFile()) return null

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

        cells?.ensureScriptConfigurations(ScriptConfigurationManager.getInstance(project),
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
                        project,
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
                    highlightingManager.passCreated(project, highlightingQueue, cells, cellUnderEditor.ordinal)
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
                    project,
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

    private fun Collection<PsiLanguageInjectionHost>?.ensureScriptConfigurations(scriptingManager: ScriptConfigurationManager, manager: InjectedLanguageManager) {
        this?.forEach {
            manager.getInjectedPsiFiles(it)?.firstOrNull { f -> f.first is KtFile }?.first?.let { ktFile ->
                if (ktFile is KtFile) {
                    scriptingManager.getConfiguration(ktFile)
                }
            }
        }
    }

    companion object {
        private val LOG = thisLogger()
    }
}

internal fun isEitherSymmetricallyContainedRange(lhs: TextRange, rhs: TextRange): Boolean = lhs.contains(rhs) || rhs.contains(lhs)

@NlsSafe
private const val SCRIPT_CLASS_ACCESS_ERROR  = "Cannot access "
@NlsSafe
private const val SCRIPT_BASE_CLASS_ACCESS_ERROR  = "Cannot access script base class"

private fun String.isMissingBaseDependencyError() =
    startsWith(SCRIPTING_MISSING_BASE_CLASS_ERROR) || startsWith(SCRIPT_BASE_CLASS_ACCESS_ERROR)

private fun String.isMissingImplicitReceiverError() =
    startsWith("[${SCRIPTING_MISSING_DEPENDENCY_PREFIX}") ||
            startsWith(SCRIPTING_MISSING_DEPENDENCY_PREFIX) ||
            startsWith(SCRIPT_CLASS_ACCESS_ERROR)


class KotlinNotebookHighlightingErrorFilter: HighlightInfoFilter {
    @Volatile
    private var reloadRequested = false

    init {
        checkCompilerListenerPersists()
    }

    private fun checkCompilerListenerPersists() {
        val currentProject = ProjectManager.getInstance().openProjects.firstOrNull {
            it.isOpen
        } ?: return
        currentProject.messageBus.connect().subscribe(NotebookCodeSnippetsChangeListener.TOPIC, object : NotebookCodeSnippetsChangeListener {
            override fun scriptsClassesChanged(file: BackedNotebookVirtualFile) {
                reloadRequested = false
            }
        })
    }

    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (file == null || !file.name.endsWith(NOTEBOOK_INJECTED_FILE_EXTENSION)) return true

        val isTargetHost = file.getUserData(NonTargetHostErrorMark) == null
        if (!isTargetHost) return true

        if (highlightInfo.severity != HighlightSeverity.ERROR) return true
        val description = highlightInfo.description ?: return true
        val isMissingBaseClass = description.isMissingBaseDependencyError()
        val isMissingReceiverClass = description.isMissingImplicitReceiverError()

        val isMissingDependency = isMissingBaseClass || isMissingReceiverClass
        if (isMissingDependency) {
            if (reloadRequested) {
                return false
            }
        } else return true

        LOG.reportErrorTestAware(
            if (isMissingBaseClass)
                "Missing base script class"
            else
                "Missing script receiver class: $description",
            Attachment(file.name, file.text)
        )

        if (isMissingReceiverClass) {
            return false
        }

        NotebookNotificationUtility.getInstance(file.project).kernelRelatedFactory
            .showAbsentInitialBaseDependenciesInfo()

        reloadRequested = true

        return false
    }

    companion object {
        private val LOG = thisLogger()
    }
}