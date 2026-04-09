// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.intellij.kotlin.jupyter.core.settings.recents.RecentNotebookState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.XCollection
import java.util.EventListener

@Service
@State(
    name = "KotlinNotebookApplicationLocalOptions",
    storages = [Storage(value = APP_LOCAL_CONFIG_FILE, roamingType = RoamingType.DISABLED)],
    category = SettingsCategory.PLUGINS
)
class KotlinNotebookApplicationLocalOptionsProvider :
    DelegatingOptionsProvider<KotlinNotebookApplicationLocalOptionsProvider.State, KotlinNotebookApplicationLocalOptionsProvider.Listener>(
        State(),
        Listener::class.java
    ), Disposable
{
    var recentNotebooks: MutableList<RecentNotebookState> by prop(State::recentNotebooks)

    override fun dispose() {
    }

    class State : BaseState() {
        @get:XCollection
        @set:XCollection
        var recentNotebooks: MutableList<RecentNotebookState> by list()
    }

    interface Listener : EventListener
}
