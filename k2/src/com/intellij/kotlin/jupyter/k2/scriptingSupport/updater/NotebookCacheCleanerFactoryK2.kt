// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.updater

import com.intellij.kotlin.jupyter.core.scriptingSupport.KotlinNotebookCacheCleaner
import com.intellij.openapi.project.Project

internal class NotebookCacheCleanerFactoryK2 : KotlinNotebookCacheCleaner.Factory {
    override fun create(project: Project): KotlinNotebookCacheCleaner {
        return KotlinNotebookCacheCleanerK2(project)
    }
}
