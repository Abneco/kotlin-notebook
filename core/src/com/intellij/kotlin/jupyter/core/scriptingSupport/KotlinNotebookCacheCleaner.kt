// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.ide.handlers.KotlinPluginModeAwareHandler
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.getOpenKotlinNotebookFiles
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Invalidates notebook-specific caches (workspace model entities, script definitions, per-file compiler services)
 * and triggers a scripting re-update.
 *
 * Used a first resort to address potential cache corruption.
 * After cleanup, [restartCompilerServiceForFiles] is triggered.
 */
abstract class KotlinNotebookCacheCleaner(protected val project: Project) : KotlinPluginModeAwareHandler {
    protected val compilerService: JupyterCompilerService by lazy {
        project.service()
    }

    suspend fun clearNotebookCaches() {
        val openNotebooks = project.getOpenKotlinNotebookFiles()
        LOG.info("Clearing notebook caches for ${openNotebooks.size} open notebooks")
        invalidateCaches(openNotebooks)
        restartCompilerServiceForFiles(openNotebooks)
    }

    abstract suspend fun invalidateCaches(notebooks: Collection<BackedNotebookVirtualFile>)

    protected fun restartCompilerServiceForFiles(notebooks: Collection<BackedNotebookVirtualFile>) {
        compilerService.resetScriptDefinition()
        for (notebook in notebooks) {
            compilerService.recreateService(notebook)
        }

        compilerService.requestScriptingUpdate()
        LOG.info("Notebook caches invalidated, scripting update requested")
    }

    interface Factory {
        fun create(project: Project): KotlinNotebookCacheCleaner
    }

    companion object {
        protected val LOG: Logger = notebookLogger()

        fun create(project: Project): KotlinNotebookCacheCleaner {
            return project.service<Factory>().create(project)
        }
    }
}
