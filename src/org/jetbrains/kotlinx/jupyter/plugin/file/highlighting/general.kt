// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.codeInsight.daemon.impl.InjectedLanguageHighlightingRangeReducer
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility.showAbsentInitialBaseDependenciesInfo
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.CompleteHighlightingRange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NonTargetHostErrorMark
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookDocumentTargetRanges
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.RenamingEnclosedRange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.notebookInjectedFileExtension
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.scheduleUpdateLater
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.scriptingMissingBaseClassError
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.scriptingMissingClassError
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile


internal class KotlinNotebookInjectedRangeReducer : InjectedLanguageHighlightingRangeReducer {
    private val notebookCodeUtility = NotebookHighlightingUtilityObject
    private val dummyTextChangeRange = TextRange(0, 0)

    override fun reduceRange(file: PsiFile, editor: Editor): Collection<TextRange>? {
        if (!notebookCodeUtility.isLooksLikeNotebookFile(file)) return null

        val jupyterFile = file as? JupyterFile ?: return null
        val document = FileDocumentManager.getInstance().getDocument(jupyterFile.virtualFile) ?: return null

        val cells = file.getNotebookCellList()
        cells?.ensureScriptConfigurations(ScriptConfigurationManager.getInstance(file.project),
                                                               InjectedLanguageManager.getInstance(file.project))
        jupyterFile.ensureScriptManagerReady()

        return synchronized(document) {
            val cellInd = document.getUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX)?.let { // notebook file is already rebuild
                cells?.get(it)
            }?.textRange
            var severalUpdates = document.getUserData(NotebookDocumentTargetRanges)
            val completeHLRange = document.getUserData(CompleteHighlightingRange)
            if (cellInd != null && (completeHLRange == null || completeHLRange.startOffset == cellInd.startOffset)) { // converge
                document.putUserData(CompleteHighlightingRange, cellInd)
                if (severalUpdates?.size == 1) {
                    document.putUserData(NotebookDocumentTargetRanges, listOf(cellInd))
                }
            }
            val afterRenaming = document.getUserData(RenamingEnclosedRange)
            if (afterRenaming?.isNotEmpty() == true) {
                return afterRenaming
            }

            if (severalUpdates?.isNotEmpty() == true) {
                if (cellInd != null && !severalUpdates.contains(cellInd)) {
                    document.putUserData(NotebookDocumentTargetRanges, severalUpdates.toMutableSet().also {
                        it.add(cellInd)
                        severalUpdates = it
                    })
                // undo action might be performed, so set proper range
                } else if (cellInd == null && !severalUpdates.isNullOrEmpty() && severalUpdates?.none { completeHLRange?.intersects(it) == true } == true) {
                    document.putUserData(CompleteHighlightingRange, severalUpdates?.firstOrNull())
                }
                return severalUpdates
            }

            document.getUserData(CompleteHighlightingRange)
        }?.let {
            listOf(TextRange(it.startOffset, it.endOffset + 1))
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

    private fun PsiFile.ensureScriptManagerReady() {
        val scriptDefManager = ScriptDefinitionsManager.getInstance(project)

        if (scriptDefManager.isReady()) {
            if (JupyterCompilerService.getInstance(project).needToUpdateImplicitsReceiversIfAny) {
                JupyterKtScriptingSupport.getInstance(project).update()
                throw ProcessCanceledException()
            }
            return
        }
        if (ApplicationManager.getApplication().isDispatchThread) {
            scheduleUpdateLater(this)
        } else throw ProcessCanceledException()
        // for some reason, in debug mode calling isReady() might cause DL
    }
}

internal fun isEitherSymmetricallyContainedRange(lhs: TextRange, rhs: TextRange): Boolean = lhs.contains(rhs) || rhs.contains(lhs)

class KotlinNotebookHighlightingErrorFilter: HighlightInfoFilter {
    private var reloadRequested = true
    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (file == null || !file.name.endsWith(notebookInjectedFileExtension)) return true
        //val errorRegistry = file.getUserData(NonTargetHostErrorRegistry) ?: return true
        val isTargetHost = file.getUserData(NonTargetHostErrorMark) == null
        if (!isTargetHost) return true

        val description = highlightInfo.description ?: return true
        if (description.startsWith(scriptingMissingBaseClassError)) {
            showAbsentInitialBaseDependenciesInfo(file.project)
            return false
        }

        return !description.startsWith(scriptReceiverErrorMsg).also {
            it.ifTrue {
                if (!reloadRequested) {
                    invokeLater { ScriptDefinitionsManager.getInstance(file.project).reloadScriptDefinitions() }
                }
                reloadRequested = true
            }
        }
    }

    companion object {
        internal fun HighlightInfo.checkIfMissingBaseClassError(): Boolean {
            val description = description ?: return false
            return description.startsWith(scriptingMissingBaseClassError)
        }

        @NlsSafe
        internal const val scriptReceiverErrorMsg = "[$scriptingMissingClassError]"
    }
}