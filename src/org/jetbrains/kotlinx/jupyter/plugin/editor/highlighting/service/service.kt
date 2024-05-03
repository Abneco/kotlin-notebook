// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.base.fe10.analysis.DaemonCodeAnalyzerStatusService
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingRestarter.UpdateSteps.performHLStartupTemplate
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.NotebookHighlightingService.Companion.HL_DELAY_PAUSE
import org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util.NotebookHighlightingUtilityObject.shouldStartAfterPreChecks
import org.jetbrains.kotlinx.jupyter.plugin.util.KotlinNotebookPluginScope
import org.jetbrains.kotlinx.jupyter.plugin.util.NotebookProjectLevelService
import org.jetbrains.kotlinx.jupyter.plugin.util.restartAnalyzing
import org.jetbrains.kotlinx.jupyter.plugin.util.withReadAccess
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile

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
        .namedChildScope("NotebookHighlightingRestarter")

    object UpdateSteps {

        suspend inline fun performHLStartupTemplate(
            file: PsiFile, delayDelta: Long,
            crossinline afterRequest: () -> Unit = {}
        ) {
            val analyzer = DaemonCodeAnalyzerStatusService.getInstance(file.project)
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
