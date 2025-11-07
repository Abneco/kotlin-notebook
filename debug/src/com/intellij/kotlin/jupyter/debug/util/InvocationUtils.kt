// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.util

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.impl.PrioritizedTask
import kotlinx.coroutines.Job

/**
 * Schedules [action] inside DebuggerManagerThread.
 */
internal fun <T> SuspendContextImpl.scheduleOnManagerThread(action: suspend () -> T) : Job {
    return executeOnDMT(this, PrioritizedTask.Priority.HIGH) {
        action()
    }
}

/**
 * Invokes [block] inside DebuggerManagerThread.
 */
internal suspend inline fun <T> EvaluationContextImpl.runOnManagerThread(crossinline block: () -> T): T =
    withDebugContext(suspendContext, PrioritizedTask.Priority.HIGH) {
        block()
    }