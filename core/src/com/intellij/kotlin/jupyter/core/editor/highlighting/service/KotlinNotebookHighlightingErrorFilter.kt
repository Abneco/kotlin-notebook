// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.NotebookHighlightingUtilityObject
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.NotebookHighlightingUtilityObject.SCRIPTING_MISSING_BASE_CLASS_ERROR
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.NotebookHighlightingUtilityObject.SCRIPTING_MISSING_DEPENDENCY_PREFIX
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.util.reportErrorTestAware
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiFile

class KotlinNotebookHighlightingErrorFilter: HighlightInfoFilter {
    private fun PsiFile.shouldAcceptFile(): Boolean {
        val notebookExtension = JupyterCompilerService.getInstance(project).fileExtension

        return name.endsWith(notebookExtension)
    }

    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (file == null || !file.shouldAcceptFile()) return true

        val isTargetHost = file.getUserData(NotebookHighlightingUtilityObject.NonTargetHostErrorMark) == null
        if (!isTargetHost) return true

        if (highlightInfo.severity != HighlightSeverity.ERROR) return true
        val description = highlightInfo.description ?: return true
        val isMissingBaseClass = description.isMissingBaseDependencyError()
        val isMissingReceiverClass = description.isMissingImplicitReceiverError()

        val isMissingDependency = isMissingBaseClass || isMissingReceiverClass

        if (isMissingDependency) {
            LOG.reportErrorTestAware(
                if (isMissingBaseClass)
                    "Missing base script class"
                else
                    "Missing script receiver class: $description",
                Attachment(file.name, file.text)
            )
        }

        // hide missing script configuration until configuration is always stable
        return !isMissingDependency
    }

    companion object {
        private val LOG = thisLogger()
    }
}


@NlsSafe
private const val SCRIPT_CLASS_ACCESS_ERROR  = "Cannot access "
@NlsSafe
private const val SCRIPT_BASE_CLASS_ACCESS_ERROR  = "Cannot access script base class"

private fun String.isMissingBaseDependencyError() =
    startsWith(SCRIPTING_MISSING_BASE_CLASS_ERROR) || startsWith(SCRIPT_BASE_CLASS_ACCESS_ERROR)

private fun String.isMissingImplicitReceiverError() =
    startsWith("[$SCRIPTING_MISSING_DEPENDENCY_PREFIX") ||
            startsWith(SCRIPTING_MISSING_DEPENDENCY_PREFIX) ||
            startsWith(SCRIPT_CLASS_ACCESS_ERROR)