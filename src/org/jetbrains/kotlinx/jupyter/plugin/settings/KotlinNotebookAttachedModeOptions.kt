// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.startup.defaultSpringAppPorts
import java.util.*
import kotlin.reflect.KMutableProperty1

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

    var hb by socket(State::hb, JupyterSocketType.HB)
    var shell by socket(State::shell, JupyterSocketType.SHELL)
    var control by socket(State::control, JupyterSocketType.CONTROL)
    var stdin by socket(State::stdin, JupyterSocketType.STDIN)
    var iopub by socket(State::iopub, JupyterSocketType.IOPUB)

    private fun socket(
        socketProperty: KMutableProperty1<State, Int>, socketType: JupyterSocketType
    ) = prop(socketProperty).onChange { old, new -> onPortChanged(socketType, old, new) }

    private val socketProperties = mapOf(
        JupyterSocketType.HB to ::hb,
        JupyterSocketType.SHELL to ::shell,
        JupyterSocketType.CONTROL to ::control,
        JupyterSocketType.STDIN to ::stdin,
        JupyterSocketType.IOPUB to ::iopub,
    )

    fun getSocketProperty(socketType: JupyterSocketType) = socketProperties[socketType]!!

    class State : BaseState() {
        var hb by socket(JupyterSocketType.HB)
        var shell by socket(JupyterSocketType.SHELL)
        var control by socket(JupyterSocketType.CONTROL)
        var stdin by socket(JupyterSocketType.STDIN)
        var iopub by socket(JupyterSocketType.IOPUB)

        private fun socket(type: JupyterSocketType) = property(defaultSpringAppPorts[type]!!)
    }

    class PresentableNameGetter : com.intellij.openapi.components.State.NameGetter() {
        override fun get(): String = KotlinNotebookBundle.message("kotlin.jupyter.settings.title")
    }

    interface Listener : EventListener {
        fun onPortChanged(type: JupyterSocketType, oldValue: Int, newValue: Int)
    }

    companion object {
        fun getInstance(project: Project): KotlinNotebookAttachedModeOptions = project.service()
    }
}
