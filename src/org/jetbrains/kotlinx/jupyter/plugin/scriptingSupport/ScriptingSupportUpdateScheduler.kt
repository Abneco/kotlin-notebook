// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.SCRIPTING_SUPPORT_TOPIC
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.ScriptingSupportAfterUpdateListener
import org.jetbrains.kotlinx.jupyter.plugin.util.SingleUpdateScheduler

class ScriptingSupportUpdateScheduler(
    project: Project,
    scheduledAction: () -> Unit,
    parentDisposable: Disposable,
    delay: Long = DEFAULT_DELAY
) : SingleUpdateScheduler(scheduledAction, parentDisposable, delay) {
    init {
      project.messageBus.connect(this)
          .subscribe(
            SCRIPTING_SUPPORT_TOPIC,
            ScriptingSupportAfterUpdateListener {
                  fireActionFinished()
              }
          )
    }

    override fun actionInvocationDone() = Unit
}