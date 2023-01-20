// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.file.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.codeInsight.daemon.impl.InjectedLanguageHighlightingRangeReducer
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.plugin.file.getNotebookCellList
import org.jetbrains.kotlinx.jupyter.plugin.file.getOrCreateForceScriptDefinitionsUpdateFlag
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.CompleteHighlightingRange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NonTargetHostErrorRegistry
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.NotebookDocumentTargetRanges
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.RenamingEnclosedRange
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.notebookInjectedFileExtension
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.scheduleUpdateLater
import org.jetbrains.kotlinx.jupyter.plugin.file.highlighting.NotebookHighlightingUtilityObject.scriptingMissingClassError
import org.jetbrains.kotlinx.jupyter.plugin.file.restartAnalyzing
import org.jetbrains.kotlinx.jupyter.plugin.file.scheduleScriptDefinitionsManagerUpdate
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
        jupyterFile.ensureScriptManagerReady(document)

        return synchronized(document) {
            val afterRenaming = document.getUserData(RenamingEnclosedRange)
            if (afterRenaming?.isNotEmpty() == true) {
                return afterRenaming
            }
            val severalUpdates = document.getUserData(NotebookDocumentTargetRanges)
            if (severalUpdates?.isNotEmpty() == true) {
                return severalUpdates
            }

            val cellInd = document.getUserData(NOTEBOOK_DOCUMENT_CELL_CHANGE_INDEX)?.let { // notebook file is already rebuild
                cells?.get(it)
            }?.textRange
            val possibleRange = document.getUserData(NOTEBOOK_DOCUMENT_TARGET_ANALYSIS_RANGE)
            //if (cellInd != null && possibleRange?.endOffset != cellInd.endOffset) cellInd else possibleRange
            if (possibleRange == null && cellInd != null) {
                document.putUserData(CompleteHighlightingRange, cellInd)
                cellInd
            } else null
        }?.let {
            listOf(TextRange(it.startOffset, it.endOffset + 2))
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

    private fun PsiFile.ensureScriptManagerReady(document: Document) {
        val scriptDefManager = ScriptDefinitionsManager.getInstance(project)

        if (scriptDefManager.isReady()) {
            document.getOrCreateForceScriptDefinitionsUpdateFlag().let {
                if (it.compareAndSet(true, false)) {
                    project.scheduleScriptDefinitionsManagerUpdate()
                    throw ProcessCanceledException()
                }
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
    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (file == null || !file.name.endsWith(notebookInjectedFileExtension)) return true
        val errorRegistry = file.getUserData(NonTargetHostErrorRegistry) ?: return true

        if (highlightInfo.severity == HighlightSeverity.ERROR) {
            if (highlightInfo.description == scriptingMissingClassError) {
                file.project.scheduleScriptDefinitionsManagerUpdate()
                invokeLater {
                    file.restartAnalyzing()
                }
                throw ProcessCanceledException()
            }
            errorRegistry.add(highlightInfo)
            return false
        }

        return true
    }
}