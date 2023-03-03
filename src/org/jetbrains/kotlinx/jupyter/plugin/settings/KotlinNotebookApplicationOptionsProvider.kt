// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.editor.JupyterNotebookGutterManager

@Service
@State(
    name = "KotlinNotebookApplicationOptions", storages = [Storage("kotlinNotebook.xml")], category = SettingsCategory.PLUGINS
)
class KotlinNotebookApplicationOptionsProvider : SimplePersistentStateComponent<KotlinNotebookApplicationOptionsProvider.State>(State()) {
    class State : BaseState() {
        var shouldShowExecutionCount by property(true)
    }

    companion object {
        internal fun refreshEditors() {
            EditorFactory.getInstance().allEditors.forEach {
                if (it.isKotlinNotebook) {
                    JupyterNotebookGutterManager.putHighlighters(it as EditorEx)
                    it.component.repaint()
                }
            }
        }
    }
}