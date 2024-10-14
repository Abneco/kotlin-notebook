// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.execution.process.ProcessEvent
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelEvent
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelListener
import com.intellij.openapi.util.NlsSafe
import java.util.*

interface KotlinKernelProcessListener : KotlinKernelListener {
    fun kernelWillTerminate(event: KotlinKernelProcessEvent) {}
    fun kernelTerminated(event: KotlinKernelProcessEvent) {}
    fun beforeNotificationStarted(event: KotlinKernelNotificationStartedEvent) {}
}

interface KotlinKernelProcessEvent : KotlinKernelEvent {
    override val source: SeparateProcessKotlinKernelRunnableHandler
    val text: String?
    val exitCode: Int
}

class KotlinKernelNotificationStartedEvent(
  val source: SeparateProcessKotlinKernelRunnableHandler,
): EventObject(source)

class KotlinKernelProcessEventImpl(
  override val source: SeparateProcessKotlinKernelRunnableHandler,
  @NlsSafe override val text: String? = null,
  override val exitCode: Int = 0,
): KotlinKernelProcessEvent, EventObject(source) {
    constructor(processEvent: ProcessEvent): this(
        (processEvent.processHandler as SeparateProcessKotlinKernelRunnableHandler.KernelOsProcessHandler).runnableHandler,
        processEvent.text,
        processEvent.exitCode
    )
}

fun KotlinKernelListener.toProcessListener(): KotlinKernelProcessListener {
    val listener = this
    return object : KotlinKernelProcessListener, KotlinKernelListener by listener {
        override fun kernelTerminated(event: KotlinKernelProcessEvent) {
            listener.kernelTerminated(event)
        }

        override fun kernelWillTerminate(event: KotlinKernelProcessEvent) {
            listener.kernelWillTerminate(event)
        }
    }
}
