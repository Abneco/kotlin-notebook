// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport.listeners

import com.intellij.util.messages.Topic


@Topic.ProjectLevel
val SCRIPTING_SUPPORT_TOPIC = Topic(ScriptingSupportUpdateEventsListener::class.java, Topic.BroadcastDirection.NONE, true)

/**
 * Listener for reflecting events from [com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterKtScriptingSupport]
 */
interface ScriptingSupportUpdateEventsListener {
    fun afterUpdate()

    fun onUpdateException(exception: Throwable): Unit = Unit

    fun onTrivialUpdate(): Unit = Unit
}