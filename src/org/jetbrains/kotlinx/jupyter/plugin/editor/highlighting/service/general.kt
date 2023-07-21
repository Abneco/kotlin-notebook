// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.codeInsight.daemon.impl.InjectedLanguageHighlightingRangeReducer
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.execution.JupyterKotlinCellExecutionCallbackFactory
import org.jetbrains.kotlinx.jupyter.plugin.editor.notifications.NotebookNotificationUtility.showAbsentInitialBaseDependenciesInfo
import org.jetbrains.kotlinx.jupyter.plugin.util.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.NonTargetHostErrorMark
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.getCellRangesInDocumentOrNull
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.notebookInjectedFileExtension
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.scheduleUpdateLater
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.scriptingMissingBaseClassError
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.scriptingMissingClassError
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingUtilityObject.scriptingMissingDependencyPrefix
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterKtScriptingSupport
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile.Companion.takeIfBacked
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.getCell
import java.util.concurrent.TimeUnit


internal class KotlinNotebookInjectedRangeReducer : InjectedLanguageHighlightingRangeReducer {
    private val notebookCodeUtility = NotebookHighlightingUtilityObject

    override fun reduceRange(file: PsiFile, editor: Editor): Collection<TextRange>? {
        if (!NotebookHighlightingUtilityObject.looksLikeNotebookFile(file)) return null

        val jupyterFile = file as? JupyterFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(jupyterFile.virtualFile) ?: return null
        if (!NotebookCellLines.hasSupport(editor)) return null

        val backedNotebook = takeIfBacked(jupyterFile.virtualFile)

        val cells = try {
            file.getNotebookCellList()
        } catch (ex: IllegalStateException) {
            LOG.debug("Can't get Notebook cells list: $ex")
            null
        }
        val project = file.project
        val caretOffSet = editor.caretModel.offset
        val cellUnderEditor = editor.getCell(document.getLineNumber(caretOffSet))
        cells?.ensureScriptConfigurations(ScriptConfigurationManager.getInstance(project),
                                                               InjectedLanguageManager.getInstance(project))
        jupyterFile.ensureScriptManagerReady(document)
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
                highlightingQueue?.addAll(JupyterKotlinCellExecutionCallbackFactory.getInstance().getLastExecutedCellsBatch(it))
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
            if (cellIndx != null && completeHLRange != null) {
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
                return afterRenaming
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

    private fun PsiFile.ensureScriptManagerReady(doc: Document) {
        val scriptDefManager = ScriptDefinitionsManager.getInstance(project)

        if (scriptDefManager.isReady()) {
            if (JupyterCompilerService.getInstance(project).needToUpdateImplicitReceiversIfAny(virtualFile, doc, true)) {
                //LOG.warn("${Thread.currentThread().id} requested loading of classes")
                throw ProcessCanceledException()
                //return
            }
            return
        }
        if (ApplicationManager.getApplication().isDispatchThread) {
            scheduleUpdateLater(this)
        } else throw ProcessCanceledException()
        // for some reason, in debug mode calling isReady() might cause DL
    }

    companion object {
        private val LOG = thisLogger()
    }
}

internal fun isEitherSymmetricallyContainedRange(lhs: TextRange, rhs: TextRange): Boolean = lhs.contains(rhs) || rhs.contains(lhs)

class KotlinNotebookHighlightingErrorFilter: HighlightInfoFilter {
    private var reloadRequested = false
    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (file == null || !file.name.endsWith(notebookInjectedFileExtension)) return true
        //val errorRegistry = file.getUserData(NonTargetHostErrorRegistry) ?: return true
        val isTargetHost = file.getUserData(NonTargetHostErrorMark) == null
        if (!isTargetHost) return true

        val description = highlightInfo.description ?: return true
        if (highlightInfo.severity == HighlightSeverity.ERROR
            && description.isLikeMissingDependencyClassError(true) && !reloadRequested) {
            showAbsentInitialBaseDependenciesInfo(file.project)
            reloadRequested = true
            return false
        }
        val reloadState = reloadRequested

        if (highlightInfo.severity == HighlightSeverity.ERROR && description.isLikeMissingDependencyClassError(false)) {
            if (reloadState) return false
            val missingClass = description.substringAfter("Cannot access ").split("\'")[1]
            val project = file.project
            val found = if (missingClass.isKTNBClass()) true
                else {
                    val fqnName = missingClass.count { it == '.' } > 0
                    val properClass = (if (fqnName) missingClass.substringAfterLast(".") else missingClass) + ".class"
                    FilenameIndex.getFilesByName(project, properClass, ProjectScope.getLibrariesScope(project)).isNotEmpty()
                }
            LOG.warn("Faced ${highlightInfo.description} error, will try to update scripting")
            if (found && !reloadState) {
                val alreadyUpdating = JupyterKtScriptingSupport.isInTheTransaction(project)
                if (!alreadyUpdating) {
                    LOG.info("Requesting reload of scripting...")
                    AppExecutorUtil.getAppScheduledExecutorService().schedule(
                        { JupyterKtScriptingSupport.update(project) }
                        , 600, TimeUnit.MILLISECONDS)
                }
                reloadRequested = true
                return false
            }
        }

        return true
    }

    companion object {
        private val LOG = thisLogger()

        internal val classRegex = Regex("Line_.+jupyter")
        internal fun String.isKTNBClass(): Boolean = matches(classRegex)
        internal fun HighlightInfo.checkIfMissingBaseClassError(): Boolean {
            val description = description ?: return false
            return description.startsWith(scriptingMissingBaseClassError)
        }

        internal fun String.isLikeMissingDependencyClassError(baseClassCheck: Boolean) =
            if (baseClassCheck) startsWith(scriptingMissingBaseClassError) || startsWith(scriptBaseClassAccessFailure)
            else startsWith("[${scriptingMissingDependencyPrefix}")
                    || startsWith(scriptingMissingDependencyPrefix) || startsWith(scriptClassAccessFailure)

        @NlsSafe
        internal const val scriptReceiverErrorMsg = "[$scriptingMissingClassError]"
        @NlsSafe
        internal const val scriptClassAccessFailure  = "Cannot access "
        @NlsSafe
        internal const val scriptBaseClassAccessFailure  = "Cannot access script base class"
    }
}