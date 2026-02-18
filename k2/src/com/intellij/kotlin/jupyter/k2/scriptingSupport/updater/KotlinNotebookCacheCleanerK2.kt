// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.updater

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.KotlinNotebookCacheCleaner
import com.intellij.kotlin.jupyter.k2.scriptingSupport.NotebookScriptConfigurationsManager
import com.intellij.openapi.project.Project

/**
 * Invalidates workspace model caches:
 *  - notebook library dependencies
 *  - all notebook entities
 */
internal class KotlinNotebookCacheCleanerK2(project: Project) : KotlinNotebookCacheCleaner(project) {
    override suspend fun invalidateCaches(notebooks: Collection<BackedNotebookVirtualFile>) {
        val configurationsManager = NotebookScriptConfigurationsManager.getInstance(project)

        // Library dependencies are removed first to avoid dangling references during the entity cleanup.
        if (notebooks.isNotEmpty()) {
            configurationsManager.clearNotebookLibraryDependencies(*notebooks.toTypedArray())
        }
        configurationsManager.clearAllNotebookEntities()
    }
}
