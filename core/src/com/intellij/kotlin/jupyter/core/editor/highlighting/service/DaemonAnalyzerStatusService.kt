// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * This service serves as an entry point to get
 * current status of [com.intellij.codeInsight.daemon.DaemonCodeAnalyzer].
 * Kotlin Notebook plugin uses this info to schedule HL restarts.
 * Replication of [DaemonCodeAnalyzerStatusService] from K1-specific module to remove dependency on it.
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class DaemonAnalyzerStatusService(project: Project) : Disposable {
    @Volatile
    var daemonRunning: Boolean = false
        private set

    init {
        project.messageBus.connect(this).subscribe(
          DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
          object : DaemonCodeAnalyzer.DaemonListener {
                override fun daemonStarting(fileEditors: Collection<FileEditor>) {
                    daemonRunning = true
                }

                override fun daemonFinished(fileEditors: Collection<FileEditor>) {
                    daemonRunning = false
                }

                override fun daemonCancelEventOccurred(reason: String) {
                    daemonRunning = false
                }
            }
        )
    }

    override fun dispose() = Unit

    companion object {
        fun getInstance(project: Project): DaemonAnalyzerStatusService = project.service()
    }
}