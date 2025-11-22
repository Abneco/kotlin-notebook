// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class KotlinNotebookChangeSessionModeActions: ActionGroup(), DumbAware {
    private val myChildren = KotlinNotebookSessionRunMode.entries
        .map(::KotlinNotebookChangeSessionModeAction)
        .toTypedArray()

    override fun getChildren(event: AnActionEvent?): Array<out AnAction> {
        return myChildren
    }

    companion object {
        const val ID: String = "KotlinNotebookChangeSessionModeActions"
    }
}
