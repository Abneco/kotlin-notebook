// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.SCRIPTING_SUPPORT_TOPIC
import com.intellij.kotlin.jupyter.core.scriptingSupport.listeners.ScriptingSupportUpdateEventsListener
import com.intellij.kotlin.jupyter.core.util.SingleUpdateScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

class ScriptingSupportUpdateScheduler(
    project: Project,
    scheduledAction: () -> Unit,
    parentDisposable: Disposable,
    delay: Long = DEFAULT_DELAY
) : SingleUpdateScheduler(scheduledAction, parentDisposable, delay) {
    init {
        project.messageBus.connect(this).subscribe(
            SCRIPTING_SUPPORT_TOPIC,
            object : ScriptingSupportUpdateEventsListener {
                override fun afterUpdate() {
                    fireActionFinished()
                }

                override fun onUpdateException(exception: Exception) {
                    fireActionFinished()
                }

                override fun onTrivialUpdate() {
                    fireActionFinished()
                }
            }
        )
    }

    override fun actionInvocationDone(): Unit = Unit
}