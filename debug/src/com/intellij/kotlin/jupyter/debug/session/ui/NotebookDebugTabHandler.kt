// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.session.ui

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebugSessionImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles UI-related operations for Kotlin Notebook debug sessions,
 * specifically managing the debug tool window tabs.
 */
internal class NotebookDebugTabHandler(
    private val project: Project,
    private val fileName: String,
    private val log: Logger
) {
    fun showSessionTab(session: XDebugSession?) {
        val sessionImpl = session as? XDebugSessionImpl
        if (sessionImpl == null) {
            log.warn("Cannot show session tab in ${fileName}: session is not XDebugSessionImpl, but ${session?.javaClass?.name}")
            return
        }
        sessionImpl.showSessionTab()
    }

    /**
     * Finds the [com.intellij.execution.ui.RunContentDescriptor] associated with the given [session].
     * In the split-mode, the session tab might not be accessed via session directly.
     */
    @RequiresEdt
    fun findSessionTabDescriptor(session: XDebugSession?): RunContentDescriptor? {
        val sessionImpl = session as? XDebugSessionImpl ?: return null
        val executor = sessionImpl.executionEnvironment?.executor
        if (executor == null || !sessionImpl.hasSessionTab) {
            log.info("Session tab is already closed or not yet created for session in $fileName")
            return null
        }

        val manager = RunContentManager.getInstance(project)
        return manager.findContentDescriptor(executor, sessionImpl.debugProcess.processHandler)
    }

    /**
     * Closes the debug session tab represented by [tabDescriptor].
     * NB: invoking this method will dispose [XDebugSessionImpl], if it was not closed already.
     */
    suspend fun closeSessionTab(tabDescriptor: RunContentDescriptor?) {
        if (tabDescriptor == null) {
            log.info("ContentDescriptor is null for session in $fileName, skipping tab closure")
            return
        }
        if (tabDescriptor.isHiddenContent) {
            log.info("Session tab is hidden for session in $fileName, skipping tab closure")
            return
        }

        withContext(Dispatchers.EDT) {
            val manager = RunContentManager.getInstance(project)
            manager.removeRunContent(DefaultDebugExecutor.getDebugExecutorInstance(), tabDescriptor)
        }
    }
}
