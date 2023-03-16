// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.codeInsight.daemon.impl.InjectedLanguageHighlightingRangeReducer
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
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
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinCellExecutionCallbackFactory
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility.showAbsentInitialBaseDependenciesInfo
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.CompleteHighlightingRange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NonTargetHostErrorMark
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookCellsUpdatesAllowedToChange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookDocumentTargetRanges
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookQueuedTargetRanges
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.RenamingEnclosedRange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.getCellRangesInDocumentOrNull
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.notebookInjectedFileExtension
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.scheduleUpdateLater
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.scriptingMissingBaseClassError
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.scriptingMissingClassError
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.scriptingMissingDependencyPrefix
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile.Companion.takeIfBacked
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.getCell


internal class KotlinNotebookInjectedRangeReducer : InjectedLanguageHighlightingRangeReducer {
    private val notebookCodeUtility = NotebookHighlightingUtilityObject
    private val dummyTextChangeRange = TextRange(0, 0)

    override fun reduceRange(file: PsiFile, editor: Editor): Collection<TextRange>? {
        if (!notebookCodeUtility.looksLikeNotebookFile(file)) return null

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
        val caretOffSet = editor.caretModel.offset
        val cellUnderEditor = editor.getCell(document.getLineNumber(caretOffSet))
        cells?.ensureScriptConfigurations(ScriptConfigurationManager.getInstance(file.project),
                                                               InjectedLanguageManager.getInstance(file.project))
        jupyterFile.ensureScriptManagerReady(document)

        return synchronized(document) {
            val cellIndx = document.getUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX)
            val cellOfChange = cellIndx?.let { // notebook file is already rebuild
                cells?.getOrNull(it)
            }
            val cellChangeRange = cellOfChange?.textRange
            val completeHLRange = document.getUserData(CompleteHighlightingRange)
            var severalUpdates = document.getUserData(NotebookDocumentTargetRanges)
            val highlightingQueue = document.getUserData(NotebookQueuedTargetRanges)
            backedNotebook?.let {
                highlightingQueue?.addAll(JupyterKotlinCellExecutionCallbackFactory.getInstance().getLastExecutedCellsList(it))
            }

            if (cellChangeRange != null && (completeHLRange == null || completeHLRange.startOffset == cellChangeRange.startOffset)) { // converge
                val correctUnderEditorInd = cellUnderEditor.ordinal
                // this might happen after redo action
                val nothingMatches =
                    completeHLRange == null && (correctUnderEditorInd - cellIndx > 0) && severalUpdates == null
                if (nothingMatches) {
                    val toPut = cells?.get(correctUnderEditorInd)?.textRange
                    val structureChangeIndicator = document.getUserData(NotebookHighlightingUtilityObject.NotebookDocumentStructureNontrivialChanged)
                    document.putUserData(CompleteHighlightingRange, toPut)
                    document.putUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX, correctUnderEditorInd)
                    // clear only if nothing structural was done
                    if (structureChangeIndicator?.get() == false) {
                        highlightingQueue?.clear()
                    }
                    highlightingQueue?.addIfNotNull(correctUnderEditorInd)
                    return listOfNotNull(toPut)
                }
                document.putUserData(CompleteHighlightingRange, cellChangeRange)
                if (severalUpdates?.size == 1) {
                    highlightingQueue?.add(cellIndx)
                    document.putUserData(NotebookDocumentTargetRanges, highlightingQueue)
                }
            }
            if (cellIndx != null && completeHLRange != null) {
                val newCompleteRange = if (cellUnderEditor.ordinal != cellIndx) {
                    highlightingQueue?.add(cellIndx)
                    cells?.get(cellUnderEditor.ordinal)?.textRange
                } else cellChangeRange
                document.putUserData(CompleteHighlightingRange, newCompleteRange)
            }
            if (cellIndx == null && severalUpdates?.size == 1) { // converge
                if (severalUpdates.first() != 0) {
                    severalUpdates = setOfNotNull(cellUnderEditor.ordinal).union(severalUpdates)
                    document.putUserData(NotebookDocumentTargetRanges, severalUpdates)
                    document.putUserData(CompleteHighlightingRange, cells?.get(cellUnderEditor.ordinal)?.textRange)
                } else return null
            }

            if (highlightingQueue != null && cells != null) {
                highlightingQueue.addIfNotNull(cellIndx)
                if (severalUpdates == null) return getCellRangesInDocumentOrNull(cells, highlightingQueue)
                highlightingQueue.addAll(severalUpdates)
            }

            val afterRenaming = document.getUserData(RenamingEnclosedRange)
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
                document.putUserData(NotebookDocumentTargetRanges, mergedUpdates)
                if (cellIndx == null && document.getUserData(CompleteHighlightingRange) == null && cellUnderEditor.ordinal != 0) {
                    highlightingQueue?.add(cellUnderEditor.ordinal)
                    document.putUserData(CompleteHighlightingRange, cells[cellUnderEditor.ordinal]?.textRange)
                }
                highlightingQueue?.add(cellUnderEditor.ordinal)
                return getCellRangesInDocumentOrNull(cells, highlightingQueue)
            }

            document.getUserData(CompleteHighlightingRange)
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
            if (JupyterCompilerService.getInstance(project).needToUpdateImplicitsReceiversIfAny) {
                invokeLater {
                    LOG.info("Requesting update of scripting after loading new classes in ${this.name}")
                    doc.getUserData(NotebookCellsUpdatesAllowedToChange)?.compareAndSet(true, false)
                    JupyterKtScriptingSupport.getInstance(project).update()
                }
                throw ProcessCanceledException()
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
                LOG.info("Requesting reload of scripting...")
                invokeLater { JupyterKtScriptingSupport.getInstance(file.project).update() }
                reloadRequested = true
                return false
            }
        }

        return true
    }

    companion object {
        private val LOG = thisLogger()

        internal val classRegex = Regex("Line_.+_jupyter")
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