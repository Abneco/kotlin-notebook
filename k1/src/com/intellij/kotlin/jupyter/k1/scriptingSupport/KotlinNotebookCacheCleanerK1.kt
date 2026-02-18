// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.KotlinNotebookCacheCleaner
import com.intellij.openapi.project.Project

internal class NotebookCacheCleanerFactoryK1 : KotlinNotebookCacheCleaner.Factory {
    override fun create(project: Project): KotlinNotebookCacheCleaner = KotlinNotebookCacheCleanerK1(project)
}

/**
 * Does nothing for K1 implementation,
 * users should resort to complete cache invalidation.
 */
internal class KotlinNotebookCacheCleanerK1(project: Project) : KotlinNotebookCacheCleaner(project) {
    override suspend fun invalidateCaches(notebooks: Collection<BackedNotebookVirtualFile>) = Unit
}
