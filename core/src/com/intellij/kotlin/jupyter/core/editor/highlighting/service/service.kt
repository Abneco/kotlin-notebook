// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingRestarter.UpdateSteps.performHLStartupTemplate
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.NotebookHighlightingService.Companion.HL_DELAY_PAUSE
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.DaemonAnalyzerStatusService
import com.intellij.kotlin.jupyter.core.editor.highlighting.service.util.NotebookHighlightingUtilityObject.shouldStartAfterPreChecks
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.NotebookProjectLevelService
import com.intellij.kotlin.jupyter.core.util.restartAnalyzing
import com.intellij.kotlin.jupyter.core.util.withReadAccess
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class NotebookHighlightingService(
    val project: Project, coroutineScope: CoroutineScope
) : NotebookProjectLevelService<NotebookHighlightingManager>(coroutineScope) {

    override fun createInstance(virtualFile: BackedNotebookVirtualFile, fileScope: CoroutineScope): NotebookHighlightingManager {
        return withReadAccess {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile.file)!!
            NotebookHighlightingManager(
              virtualFile, document,
              this@NotebookHighlightingService,
              fileScope,
              null)
        }
    }

    companion object {
        const val HL_DELAY_PAUSE: Long = 300
        fun getInstance(project: Project) = project.service<NotebookHighlightingService>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): NotebookHighlightingManager {
            return getInstance(project).getOrCreate(virtualFile)
        }

        fun VirtualFile?.getHighlightingManagerForFile(project: Project) =
            this?.let(BackedNotebookVirtualFile::takeIfBacked)?.let { getForFile(project, it) }
    }
}


internal object NotebookHighlightingRestarter {
    private var updateJob: Job? = null
    private val regularUpdateScope = KotlinNotebookPluginScope.global
        .childScope("NotebookHighlightingRestarter")

    object UpdateSteps {

        suspend inline fun performHLStartupTemplate(
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
    }

    fun scheduleRegularUpdateNoChecks(
        file: PsiFile,
        delayDelta: Long = HL_DELAY_PAUSE
    ) {
        regularUpdateScope.launch {
            delay(delayDelta)
            readAction { file.restartAnalyzing() }
        }
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
