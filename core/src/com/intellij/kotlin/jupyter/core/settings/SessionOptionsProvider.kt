// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.jetbrains.kotlinx.jupyter.api.SessionOptions
import java.util.*
import kotlin.reflect.full.declaredMemberProperties

@Service
@State(
    name = "SessionOptionsProvider",
    storages = [Storage(APP_CONFIG_FILE)],
    category = SettingsCategory.PLUGINS
)
class SessionOptionsProvider:
    DelegatingOptionsProvider<SessionOptionsProvider.State, SessionOptionsProvider.Listener>(State(), Listener::class.java), SessionOptions
{

    override var resolveSources: Boolean by prop(State::resolveSources).onChange(Listener::onResolveSourcesChanged)
    override var serializeScriptData: Boolean by prop(State::serializeScriptData).onChange(Listener::onSerializeScriptDataChanged)

    class State: BaseState(), SessionOptions {
        override var resolveSources: Boolean by property(true)
        override var serializeScriptData: Boolean by property(true)
    }

    interface Listener : EventListener {
        fun onResolveSourcesChanged(oldValue: Boolean, newValue: Boolean) {}
        fun onSerializeScriptDataChanged(oldValue: Boolean, newValue: Boolean) {}
    }
}

fun SessionOptions.generateSnippet(): String {
    val options = this
    return buildString {
        for (property in SessionOptions::class.declaredMemberProperties) {
            append("SessionOptions.")
            append(property.name)
            append(" = ")
            append(property.get(options))
            append("\n")
        }
    }
}
