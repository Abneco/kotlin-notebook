// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.UnindexedFilesScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager

class IndexAwareScriptDefinitionsLoadRequestor(private val project: Project) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun reloadDefinitions() {
        if (project.isDisposed || !project.isInitialized) return

        if (!UnindexedFilesScanner.isProjectContentFullyScanned(project)) return

        coroutineScope.async {
            ScriptDefinitionsManager.getInstance(project).allDefinitions
        }
    }
}