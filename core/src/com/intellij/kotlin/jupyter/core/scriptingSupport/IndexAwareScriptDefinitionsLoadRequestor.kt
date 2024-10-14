// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager

internal class IndexAwareScriptDefinitionsLoadRequestor(private val project: Project) {
    fun reloadDefinitions() {
        if (project.isDisposed || !project.isInitialized) return

        KotlinNotebookPluginScope.getForProject(project).async {
            smartReadAction(project) {
                ScriptDefinitionsManager.getInstance(project).allDefinitions
            }
        }
    }
}