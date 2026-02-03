// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.settings

import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.DelegatingOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.prop
import com.intellij.kotlin.jupyter.k2.scriptingSupport.fir.NotebookAnalysisBundledCompilerPlugins
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.EventListener


/**
 * Options provider dedicated only for K2 plugin mode.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "KotlinNotebookOptionsProviderK2",
    presentableName = KotlinNotebookK2ProjectOptionsProvider.PresentableNameGetter::class,
    storages = [Storage("kotlinNotebook.xml")],
    category = SettingsCategory.PLUGINS
)
class KotlinNotebookK2ProjectOptionsProvider
    : DelegatingOptionsProvider<KotlinNotebookK2ProjectOptionsProvider.State, KotlinNotebookK2ProjectOptionsProvider.Listener>(
        State(),
        Listener::class.java
    ), Disposable
{
    var bundledCompilerPlugins by prop(
        State::bundledCompilerPlugins
    ).onChange(Listener::onCompilerPluginsChanged)

    class State : BaseState() {
        var bundledCompilerPlugins by list<NotebookAnalysisBundledCompilerPlugins>()
    }

    class PresentableNameGetter : State.NameGetter() {
        override fun get(): String = KotlinNotebookBundle.message("kotlin.jupyter.settings.k2.title")
    }

    interface Listener : EventListener {
        fun onCompilerPluginsChanged() {}
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): KotlinNotebookK2ProjectOptionsProvider = project.service()
    }
}