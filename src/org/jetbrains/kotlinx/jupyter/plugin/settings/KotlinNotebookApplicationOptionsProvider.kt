// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service
@State(
    name = "KotlinNotebookApplicationOptions", storages = [Storage("kotlinNotebook.xml")], category = SettingsCategory.PLUGINS
)
class KotlinNotebookApplicationOptionsProvider : SimplePersistentStateComponent<KotlinNotebookApplicationOptionsProvider.State>(State()) {
    class State : BaseState() {
        var shouldShowExecutionCount by property(true)
    }
}