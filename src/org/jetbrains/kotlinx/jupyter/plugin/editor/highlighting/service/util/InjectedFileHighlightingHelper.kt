// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.isEitherSymmetricallyContainedRange
import org.jetbrains.kotlinx.jupyter.plugin.editor.notifications.NotebookNotificationUtility
import org.jetbrains.kotlinx.jupyter.plugin.util.toBackedNotebookFile
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import java.util.concurrent.atomic.AtomicReference

class InjectedFileHighlightingHelper(private val injectedFile: PsiFile) {
    private val project = injectedFile.project
    private lateinit var targetHost: PsiLanguageInjectionHost
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
                ?.let { NotebookHighlightingService.getForFile(project, it) }
        targetHost = highlightingManager?.tryGetKnownHostFor(injectedFile)
            ?: injectedManager.getInjectionHost(injectedFile) ?: return false

        shouldHighlightErrors = highlightingManager?.isFileTarget(injectedFile)
            ?: checkIfHostIsTargetManually()

        return true
    }

    private fun getCompleteAnalysisRangeForWholeNotebook(injectedFile: PsiFile): TextRange? {
        val project = injectedFile.project
        val manager = InjectedLanguageManager.getInstance(project)
        val topLevelFile = manager.getTopLevelFile(injectedFile)
        topLevelFile.virtualFile.toBackedNotebookFile()
        return topLevelFile.virtualFile.toBackedNotebookFile()?.let {
            NotebookHighlightingService.getForFile(project, it).dataController.completeHighlightingRange
        }
    }

    private fun checkIfHostIsTargetManually(): Boolean {
        completeAnalysisRange = getCompleteAnalysisRangeForWholeNotebook(injectedFile)
        return completeAnalysisRange?.contains(targetHost.textRange)
                ?:
               (completeAnalysisRange != null && isEitherSymmetricallyContainedRange(completeAnalysisRange!!, targetHost.textRange))
    }

    val isCurrentFileTarget: Boolean get() = shouldHighlightErrors

    fun markTargetHost() {
        injectedFile.putUserData(NotebookHighlightingUtilityObject.NonTargetHostErrorMark, if (shouldHighlightErrors) null else true)
    }

    fun applyReceivedHighlightInfos(foundData: Collection<HighlightInfo>, holder: HighlightInfoHolder) {
        val errorRef = targetHost.getErrorPresenceIndicator()
                       ?: AtomicReference(foundData.isNotEmpty()).also { targetHost.putUserData(
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

    fun shouldAcceptDiagnostic(diagnostic: Diagnostic): Boolean {
        val info = diagnostic.factory.name
        if (info.startsWith(NotebookHighlightingUtilityObject.SCRIPTING_MISSING_BASE_CLASS_ERROR)) {
            NotebookNotificationUtility.getInstance(diagnostic.psiFile.project)
                .kernelRelatedFactory.showAbsentInitialBaseDependenciesInfo()
            return false
        }
        return info != NotebookHighlightingUtilityObject.SCRIPTING_MISSING_CLASS_ERROR
    }

}