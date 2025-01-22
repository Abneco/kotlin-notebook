// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service

import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingService.Companion.HL_DELAY_PAUSE
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.DaemonAnalyzerStatusService
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.NotebookHighlightingUtilityObject.shouldStartAfterPreChecks
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.restartAnalyzing
import com.intellij.openapi.application.readAction
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal object NotebookHighlightingRestarter {
    private var updateJob: Job? = null
    private val regularUpdateScope = KotlinNotebookPluginScope.global
        .childScope("NotebookHighlightingRestarter")

    private suspend inline fun performHLStartupTemplate(
        file: PsiFile, delayDelta: Long,
        crossinline afterRequest: () -> Unit = {}
    ) {
        val analyzer = DaemonAnalyzerStatusService.getInstance(file.project)
        delay(delayDelta)
        while (analyzer.daemonRunning) {
            delay(150)
        }
        readAction {
            file.restartAnalyzing()
        }
        afterRequest()
    }

    inline fun scheduleRegularUpdate(
        file: PsiFile,
        delayDelta: Long = HL_DELAY_PAUSE,
        crossinline afterRequest: () -> Unit = {},
        crossinline undoRequest: () -> Unit = {}
    ) {
        if (!shouldStartAfterPreChecks(file, updateJob, afterRequest, undoRequest)) return
        updateJob = regularUpdateScope.launch {
            performHLStartupTemplate(file, delayDelta, afterRequest)
        }
    }
}