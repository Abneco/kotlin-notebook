// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * This service is responsible for Kotlin-mode specific handling of force scripting update request.
 * Main use-case -- support for [RestartKotlinNotebookHighlightingAction].
 * Typically, updates are controlled by session lifecycle; this interface provides a way to do so manually.
 */
interface NotebookScriptingForceUpdateRequestor : KotlinPluginModeAwareHandler {
    /**
     * Requests update to be executed with the corresponding scripting implementation.
     */
    suspend fun forceUpdateScripting(notebooks: List<BackedNotebookVirtualFile>)

    interface Factory {
        fun create(project: Project): NotebookScriptingForceUpdateRequestor
    }

    companion object {
        fun create(project: Project): NotebookScriptingForceUpdateRequestor {
            return project.service<Factory>().create(project)
        }
    }
}