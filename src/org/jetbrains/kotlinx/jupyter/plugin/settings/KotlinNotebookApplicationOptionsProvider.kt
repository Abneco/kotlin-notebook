// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.editor.JupyterNotebookGutterManager
import java.util.EventListener

@Service
@State(
    name = "KotlinNotebookApplicationOptions",
    storages = [Storage("kotlinNotebookApp.xml")],
    category = SettingsCategory.PLUGINS
)
class KotlinNotebookApplicationOptionsProvider :
    DelegatingOptionsProvider<KotlinNotebookApplicationOptionsProvider.State, KotlinNotebookApplicationOptionsProvider.Listener>(
        State(),
        Listener::class.java
    ), Disposable
{
    init {
        addListener(object : Listener {
            override fun onShowExecutionCountChanged() {
                refreshEditors()
            }
        }, this)
    }

    var shouldShowExecutionCount by prop(State::shouldShowExecutionCount).onChange(Listener::onShowExecutionCountChanged)

    class State : BaseState() {
        var shouldShowExecutionCount by property(true)
    }

    interface Listener : EventListener {
        fun onShowExecutionCountChanged() {}
    }

    override fun dispose() {
    }

    companion object {
        private fun refreshEditors() {
            EditorFactory.getInstance().allEditors.forEach {
                if (it.isKotlinNotebook) {
                    JupyterNotebookGutterManager.putHighlighters(it as EditorEx)
                    it.component.repaint()
                }
            }
        }
    }
}
