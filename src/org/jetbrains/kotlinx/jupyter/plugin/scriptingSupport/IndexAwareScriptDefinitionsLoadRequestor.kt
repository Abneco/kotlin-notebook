// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager

class IndexAwareScriptDefinitionsLoadRequestor(private val project: Project) {
    fun reloadDefinitions() {
        if (project.isDisposed || !project.isInitialized) return

        // TODO: change to Plugin scope once ready
        project.coroutineScope.async {
            smartReadAction(project) {
                ScriptDefinitionsManager.getInstance(project).allDefinitions
            }
        }
    }
}