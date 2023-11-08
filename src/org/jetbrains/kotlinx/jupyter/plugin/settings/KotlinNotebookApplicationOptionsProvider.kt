// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
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

    var shouldShowExecutionCount by prop(State::shouldShowExecutionCount).onChange(Listener::onShowExecutionCountChanged)
    var shouldShowFoldings by prop(State::shouldShowFoldings).onChange(Listener::onShowFoldings)

    var showLetsPlotAsSwing by prop(State::showLetsPlotAsSwing)
    var showDataFrameAsSwing by prop(State::showDataFrameAsSwing)

    class State : BaseState() {
        var shouldShowExecutionCount by property(true)
        var shouldShowFoldings by property(true)

        var showLetsPlotAsSwing by property(letsPlotSwingOutputsEnabled)
        var showDataFrameAsSwing by property(isSwingUiEnabledForKotlinDataframe)
    }

    interface Listener : EventListener {
        fun onShowExecutionCountChanged() {}

        fun onShowFoldings(oldValue: Boolean, newValue: Boolean) {}
    }

    override fun dispose() {
    }
}
