// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.actions

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.scratch.RootType
import com.intellij.kotlin.jupyter.core.projectWizard.KotlinNotebookRootTypeInstance
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.get
import com.intellij.openapi.project.DumbAware


class KotlinNotebookCreateAction : KotlinNotebookCreateActionBase() {
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.disableIf { isKotlinNotebookScratchRootSelected(it) }
    }
}

class KotlinNotebookCreateActionPrioritizedGroup : ActionGroup(), DumbAware {
    private val myChildren: Array<AnAction> = arrayOf(
        object : KotlinNotebookCreateActionBase() {},
        Separator.getInstance(),
    )

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.disableIf { !isKotlinNotebookScratchRootSelected(it) }
    }

    override fun getChildren(event: AnActionEvent?): Array<AnAction> = myChildren
}

private fun AnActionEvent.disableIf(condition: (AnActionEvent) -> Boolean) {
    if (!presentation.isEnabledAndVisible) return
    if (condition(this)) {
        presentation.isEnabledAndVisible = false
    }
}

/**
 * Returns true if one of the selected items is the "Kotlin Notebooks" scratch root.
 */
private fun isKotlinNotebookScratchRootSelected(e: AnActionEvent): Boolean {
    if (e.place != ActionPlaces.PROJECT_VIEW_POPUP) return false
    val selectedItems = e.selectedItems ?: return false
    return selectedItems
        .any { item ->
            val viewNode = item as? ProjectViewNode<*> ?: return@any false
            (viewNode.value as? RootType) === KotlinNotebookRootTypeInstance
        }
}

private val AnActionEvent.selectedItems: Array<Any>?
    get() = dataContext[PlatformDataKeys.SELECTED_ITEMS]
