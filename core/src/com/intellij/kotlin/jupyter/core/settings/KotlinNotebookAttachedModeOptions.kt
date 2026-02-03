// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.protocol.startup.ANY_HOST_NAME
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.startup.DEFAULT_SPRING_APP_WEBSOCKET_PORT
import org.jetbrains.kotlinx.jupyter.ws.WsKernelPorts
import java.util.EventListener

@Service(Service.Level.PROJECT)
@State(
    name = "KotlinNotebookAttachedModeOptions",
    presentableName = KotlinNotebookAttachedModeOptions.PresentableNameGetter::class,
    storages = [Storage("kotlinNotebook.xml")],
    category = SettingsCategory.PLUGINS
)
class KotlinNotebookAttachedModeOptions:
    DelegatingOptionsProvider<KotlinNotebookAttachedModeOptions.State, KotlinNotebookAttachedModeOptions.Listener>(
        State(),
        Listener::class.java
    )
{

    var host: String by propNarrowing(State::host) { it ?: ANY_HOST_NAME }
    var webSocketPort: Int by prop(State::webSocketPort).onChange { old, new -> onPortChanged(old, new) }

    fun getKernelPorts(): KernelPorts = WsKernelPorts(webSocketPort)

    class State : BaseState() {
        var host: String? by string(ANY_HOST_NAME)
        var webSocketPort: Int by property(DEFAULT_SPRING_APP_WEBSOCKET_PORT)
    }

    class PresentableNameGetter : com.intellij.openapi.components.State.NameGetter() {
        override fun get(): String = KotlinNotebookBundle.message("kotlin.jupyter.settings.title")
    }

    interface Listener : EventListener {
        fun onPortChanged(oldValue: Int, newValue: Int)
    }

    companion object {
        fun getInstance(project: Project): KotlinNotebookAttachedModeOptions = project.service()
    }
}
