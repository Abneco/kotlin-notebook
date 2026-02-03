// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.settings

import com.intellij.kotlin.jupyter.core.settings.DelegatingOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.prop
import com.intellij.kotlin.jupyter.debug.i18n.KotlinNotebookDebugBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.EventListener


@Service(Service.Level.PROJECT)
@State(
    name = "KotlinNotebookDebugOptionsProvider",
    presentableName = KotlinNotebookDebugProjectOptionsProvider.PresentableNameGetter::class,
    storages = [Storage("kotlinNotebook.xml")],
    category = SettingsCategory.PLUGINS
)
class KotlinNotebookDebugProjectOptionsProvider
    : DelegatingOptionsProvider<KotlinNotebookDebugProjectOptionsProvider.State, KotlinNotebookDebugProjectOptionsProvider.Listener>(
    State(),
    Listener::class.java
), Disposable {
    var shouldShowNotebookVariables: Boolean by prop(State::shouldShowNotebookVariables)
        internal set
    var shouldFocusOnVariables: Boolean by prop(State::shouldFocusOnNotebookVariables)
        internal set
    var shouldNavigateToEditorOnSessionStop: Boolean by prop(State::shouldNavigateToEditorOnSessionStop)
        internal set

    class State : BaseState() {
        var shouldShowNotebookVariables: Boolean by property(true)
        var shouldFocusOnNotebookVariables: Boolean by property(false)
        var shouldNavigateToEditorOnSessionStop: Boolean by property(true)
    }

    class PresentableNameGetter : State.NameGetter() {
        override fun get(): String = KotlinNotebookDebugBundle.message("kotlin.jupyter.settings.jvm.debug")
    }

    interface Listener : EventListener

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): KotlinNotebookDebugProjectOptionsProvider = project.service()
    }
}