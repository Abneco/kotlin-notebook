// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlinx.jupyter.plugin.util.getVirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import com.intellij.jupyter.core.jupyter.actions.JupyterOpenNotebookInBrowserAction
import com.intellij.jupyter.core.jupyter.editor.actions.JupyterActionPresentationModifier
import kotlin.reflect.KClass

class KotlinNotebookActionPresentationModifier : JupyterActionPresentationModifier {
    private val disableJupyterCoreActions get() = Registry.`is`("kotlin.notebook.disable.jupyter.core.actions", false)

    private val coreActionsToDisable: List<String> = listOf(
        "JupyterCreateFileAction",
        "JupyterDebugAction"
    )

    private val editorActionsToDisableInKotlinNotebook = listOf(JupyterOpenNotebookInBrowserAction::class)

    private val AnActionEvent.isKotlinNotebookEvent: Boolean get() = getVirtualFile().isKotlinNotebook
    private fun AnActionEvent.disable() {
        presentation.isEnabledAndVisible = false
    }

    @JvmName("isOneOfClasses")
    private fun AnAction.isOneOf(actionClasses: Collection<KClass<out AnAction>>): Boolean {
        return actionClasses.any { it.isInstance(this) }
    }

    @JvmName("isOneOfStrings")
    private fun AnAction.isOneOf(actionClasses: Collection<String>): Boolean {
        val actionId: String = ActionManager.getInstance().getId(this) ?: return false
        return actionClasses.any { it == actionId }
    }

    override fun modifyPresentation(action: AnAction, event: AnActionEvent) {
        if (action.isOneOf(editorActionsToDisableInKotlinNotebook)) {
            if (event.isKotlinNotebookEvent) {
                event.disable()
            }
        } else if (action.isOneOf(coreActionsToDisable)) {
            if (disableJupyterCoreActions) {
                event.disable()
            }
        }
    }
}