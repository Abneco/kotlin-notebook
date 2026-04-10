// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.intellij.kotlin.jupyter.core.settings.recents.RecentNotebookState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import java.util.EventListener

@Service
@State(
    name = "KotlinNotebookApplicationOptions",
    storages = [Storage(APP_CONFIG_FILE)],
    category = SettingsCategory.PLUGINS
)
class KotlinNotebookApplicationOptionsProvider :
    DelegatingOptionsProvider<KotlinNotebookApplicationOptionsProvider.State, KotlinNotebookApplicationOptionsProvider.Listener>(
        State(),
        Listener::class.java
    ), Disposable
{

    var shouldShowExecutionCount: Boolean by prop(State::shouldShowExecutionCount).onChange(Listener::onShowExecutionCountChanged)
    var shouldStopExecutionOnFailure: Boolean by prop(State::shouldStopExecutionOnFailure)

    var shouldShowFoldings: Boolean by prop(State::shouldShowFoldings).onChange(Listener::onShowFoldings)

    var showLetsPlotAsSwing: Boolean by prop(State::showLetsPlotAsSwing)
    var showDataFrameAsSwing: Boolean by prop(State::showDataFrameAsSwing)
    var replCompilerMode: ReplCompilerMode by prop(
        State::replCompilerMode
    ).onChange(Listener::onReplCompilerModeChanged)

    override fun loadState(state: State) {
        migrateRecentNotebooks(state)
        super.loadState(state)
    }

    class State : BaseState() {
        var shouldShowExecutionCount: Boolean by property(true)
        var shouldStopExecutionOnFailure: Boolean by property(true)

        var shouldShowFoldings: Boolean by property(true)

        var showLetsPlotAsSwing: Boolean by property(letsPlotSwingOutputsEnabled)
        var showDataFrameAsSwing: Boolean by property(isSwingUiEnabledForKotlinDataframe)
        var replCompilerMode: ReplCompilerMode by enum(ReplCompilerMode.K1)

        @get:XCollection
        @set:XCollection
        var recentNotebooks: MutableList<RecentNotebookState> by list()
    }

    interface Listener : EventListener {
        fun onShowExecutionCountChanged() {}
        fun onShowFoldings(oldValue: Boolean, newValue: Boolean) {}
        fun onReplCompilerModeChanged() {}
    }

    override fun dispose() {
    }

    private fun migrateRecentNotebooks(oldState: State) {
        if (oldState.recentNotebooks.isEmpty()) return
        val newOptions = service<KotlinNotebookApplicationLocalOptionsProvider>()
        if (newOptions.recentNotebooks.isEmpty()) {
            newOptions.recentNotebooks.addAll(oldState.recentNotebooks)
        }
        oldState.recentNotebooks.clear()
    }
}
