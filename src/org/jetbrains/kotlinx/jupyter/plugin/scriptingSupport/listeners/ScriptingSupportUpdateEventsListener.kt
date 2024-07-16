// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners

import com.intellij.util.messages.Topic


@Topic.ProjectLevel
val SCRIPTING_SUPPORT_TOPIC = Topic(ScriptingSupportUpdateEventsListener::class.java, Topic.BroadcastDirection.NONE, true)

/**
 * Listener for reflecting events from [org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterKtScriptingSupport]
 */
interface ScriptingSupportUpdateEventsListener {
    fun afterUpdate()

    fun onUpdateException(exception: Exception) = Unit

    fun onTrivialUpdate() = Unit
}