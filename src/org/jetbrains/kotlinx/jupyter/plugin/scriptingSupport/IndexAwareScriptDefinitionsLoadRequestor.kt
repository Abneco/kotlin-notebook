// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager

internal class IndexAwareScriptDefinitionsLoadRequestor(private val project: Project) {
    fun reloadDefinitions() {
        if (project.isDisposed || !project.isInitialized) return

        // TODO: change to Plugin scope once ready
        (project as ComponentManagerEx).getCoroutineScope().async {
            smartReadAction(project) {
                ScriptDefinitionsManager.getInstance(project).allDefinitions
            }
        }
    }
}