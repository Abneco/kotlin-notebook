// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelEvent
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelListener
import java.util.EventListener
import java.util.EventObject

interface KotlinKernelProcessListener : EventListener {
    fun kernelTerminated(event: KotlinKernelProcessEvent) {}
    fun beforeNotificationStarted(event: KotlinKernelNotificationStartedEvent) {}
}

interface KotlinKernelProcessEvent : KotlinKernelEvent {
    override val source: KotlinKernelProcessHandler
    val text: String?
    val exitCode: Int
}

class KotlinKernelNotificationStartedEvent(
    val source: KotlinKernelProcessHandler,
): EventObject(source)

class KotlinKernelProcessEventImpl(
    override val source: KotlinKernelProcessHandler,
    @NlsSafe override val text: String? = null,
    override val exitCode: Int = 0,
): KotlinKernelProcessEvent, EventObject(source) {
    constructor(processEvent: ProcessEvent): this(
        processEvent.processHandler as KotlinKernelProcessHandler,
        processEvent.text,
        processEvent.exitCode
    )
}

fun KotlinKernelListener.toProcessListener(): KotlinKernelProcessListener {
    val listener = this
    return object : KotlinKernelProcessListener {
        override fun kernelTerminated(event: KotlinKernelProcessEvent) {
            listener.kernelTerminated(event)
        }
    }
}
