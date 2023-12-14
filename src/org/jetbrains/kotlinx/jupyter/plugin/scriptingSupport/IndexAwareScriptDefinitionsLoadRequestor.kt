// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.util.indexing.UnindexedFilesScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager

class IndexAwareScriptDefinitionsLoadRequestor(private val project: Project) {
    @Volatile
    private var activityPassed = false
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun reloadDefinitions() {
        if (project.isDisposed) return
        if (activityPassed && UnindexedFilesScanner.isProjectContentFullyScanned(project)) {
            coroutineScope.async {
                //TODO: .reloadScriptDefinitions() would probably match better, depends on whether the method assumes warmup or reload from scratch
                ScriptDefinitionsManager.getInstance(project).allDefinitions
            }
            return
        }
        val startupManager = StartupManager.getInstance(project)
        if (!startupManager.postStartupActivityPassed()) {
            startupManager.runAfterOpened {
                activityPassed = true
            }
        }
    }
}