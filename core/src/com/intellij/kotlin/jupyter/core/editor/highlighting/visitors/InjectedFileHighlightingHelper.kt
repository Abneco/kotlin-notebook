// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.visitors

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.editor.highlighting.NotebookHighlightingService
import com.intellij.kotlin.jupyter.core.editor.highlighting.utils.NotebookHighlightingUtilityObject
import com.intellij.kotlin.jupyter.core.editor.highlighting.utils.getErrorPresenceIndicator
import com.intellij.kotlin.jupyter.core.editor.highlighting.utils.isEitherSymmetricallyContainedRange
import com.intellij.kotlin.jupyter.core.notifications.notebookNotifications
import com.intellij.kotlin.jupyter.core.util.toKotlinNotebookBackedFile
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.atomic.AtomicReference

class InjectedFileHighlightingHelper(private val injectedFile: KtFile) {
    private val project = injectedFile.project
    private lateinit var hostInFocus: PsiLanguageInjectionHost
    private val injectedManager = InjectedLanguageManager.getInstance(project)
    private var completeAnalysisRange: TextRange? = null
    val topLevelFile: PsiFile? = injectedManager.getTopLevelFile(injectedFile)
    init {
        assert(tryUpdateCurrentInjectedFileTarget())
    }
    private var shouldHighlightErrors: Boolean = false

    private fun tryUpdateCurrentInjectedFileTarget(): Boolean {
        val highlightingManager =
            topLevelFile?.virtualFile?.let(BackedNotebookVirtualFile.Companion::takeIfBacked)
                ?.let { NotebookHighlightingService.Companion.getForFile(project, it) }
        hostInFocus = injectedManager.getInjectionHost(injectedFile) ?: return false

        shouldHighlightErrors = highlightingManager?.isFileTarget(injectedFile)
            ?: checkIfHostIsTargetManually()

        return true
    }

    private fun getCompleteAnalysisRangeForWholeNotebook(injectedFile: PsiFile): TextRange? {
        val project = injectedFile.project
        val manager = InjectedLanguageManager.getInstance(project)
        val topLevelFile = manager.getTopLevelFile(injectedFile)
        return topLevelFile.virtualFile.toKotlinNotebookBackedFile()?.let {
            NotebookHighlightingService.Companion.getForFile(project, it).focusInformation?.range
        }
    }

    private fun checkIfHostIsTargetManually(): Boolean {
        completeAnalysisRange = getCompleteAnalysisRangeForWholeNotebook(injectedFile)
        return completeAnalysisRange?.contains(hostInFocus.textRange)
                ?:
               (completeAnalysisRange != null && isEitherSymmetricallyContainedRange(completeAnalysisRange!!, hostInFocus.textRange))
    }

    val isCurrentFileInFocus: Boolean get() = shouldHighlightErrors

    fun markTargetHost() {
        injectedFile.putUserData(NotebookHighlightingUtilityObject.NonTargetHostErrorMark, if (shouldHighlightErrors) null else true)
    }

    fun applyReceivedHighlightInfos(foundData: Collection<HighlightInfo>, holder: HighlightInfoHolder) {
        val errorRef = hostInFocus.getErrorPresenceIndicator()
                       ?: AtomicReference(foundData.isNotEmpty()).also { hostInFocus.putUserData(
                         NotebookHighlightingUtilityObject.InjectedHostHasErrors, it) }

        val seenInfosOffsets = mutableSetOf<Int>()
        if (foundData.isNotEmpty()) errorRef.set(true)
        else errorRef.compareAndSet(true, false)

        for (el in foundData) {
            if (seenInfosOffsets.add(el.startOffset) && seenInfosOffsets.add(el.endOffset)) {
                holder.add(el)
            }
        }
    }

    fun shouldAcceptDiagnostic(psiElement: PsiElement, factoryName: String): Boolean {
        if (factoryName.startsWith(NotebookHighlightingUtilityObject.SCRIPTING_MISSING_BASE_CLASS_ERROR)) {
            psiElement.project.notebookNotifications.showAbsentInitialBaseDependenciesInfo()
            return false
        }
        return factoryName != NotebookHighlightingUtilityObject.SCRIPTING_MISSING_CLASS_ERROR
    }

}