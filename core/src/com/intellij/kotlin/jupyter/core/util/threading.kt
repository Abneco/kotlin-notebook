// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.progress.util.BackgroundTaskUtil.BackgroundTask
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ComputableWithName<T>(@NlsSafe private val name: String, private val action: () -> T): (() -> T) by action {
    override fun toString(): String {
        return name
    }
}

class ExecutedOnceBackgroundTask<T> private constructor(
    private var restartOnErrorAttemptsCount: Int = 0,
    action: () -> T,
) : Disposable {
    private var _action: (() -> T)? = action
    private var _bgTask: BackgroundTask<T>? = null

    @Volatile
    private var _result: Any? = null
    private val _wasStarted = AtomicBoolean(false)
    @Volatile
    private var _completedSuccessfully = false

    fun startIfNotStarted() {
        if(!_wasStarted.compareAndSet(false, true)) return

        start()
    }

    private fun start() {
        val action = _action ?: error("Task was already executed")
        val bgTask = BackgroundTaskUtil.submitTask<T>(AppExecutorUtil.getAppExecutorService(), this, action)
        bgTask.future.whenComplete { result: T?, exception: Throwable? ->
            if (exception != null) {
                LOG.warn("Background task failed, attempts left: ${restartOnErrorAttemptsCount}", exception)

                if (restartOnErrorAttemptsCount > 0) {
                    --restartOnErrorAttemptsCount
                    start()
                    return@whenComplete
                }
            } else {
                _result = result
                _completedSuccessfully = true
            }

            _action = null
            _bgTask = null
        }

        _bgTask = bgTask
    }

    fun join() {
        val bgTask = _bgTask ?: return
        bgTask.awaitCompletion()
    }

    val isCompletedSuccessfully get() = _completedSuccessfully

    val result get(): T {
        assert(_completedSuccessfully)
        @Suppress("UNCHECKED_CAST")
        return _result as T
    }

    override fun dispose() {
        _bgTask?.cancel()
        _bgTask = null
        _action = null
    }

    companion object {
        private val LOG = logger<ExecutedOnceBackgroundTask<*>>()

        fun <T> create(
            restartOnErrorAttemptsCount: Int = 0,
            parentDisposable: Disposable,
            action: () -> T,
        ): ExecutedOnceBackgroundTask<T> {
            val task = ExecutedOnceBackgroundTask(restartOnErrorAttemptsCount, action)
            Disposer.register(parentDisposable, task)
            return task
        }
    }
}


sealed class UpdateScheduler(
    protected val updateAction: () -> Unit,
    protected val delay: Long = DEFAULT_DELAY
) {
    abstract fun requestUpdate()

    companion object {
        const val DEFAULT_DELAY: Long = 300
    }
}


open class SingleUpdateScheduler(
    scheduledAction: () -> Unit,
    parentDisposable: Disposable,
    delay: Long = DEFAULT_DELAY
) : UpdateScheduler(scheduledAction, delay), Disposable {
    init {
        Disposer.register(parentDisposable, this)
    }

    private val scheduler: ScheduledExecutorService = ConcurrencyUtil
        .newSingleScheduledThreadExecutor("UpdateRequestor")

    private val isUpdateRequested = AtomicBoolean(false)
    private var scheduledFuture: ScheduledFuture<*>? = null

    private val updateRunnable: Runnable = Runnable {
        try {
            updateAction.invoke()
        } finally {
            actionInvocationDone()
        }
    }

    @Synchronized
    override fun requestUpdate() {
        if (!isUpdateRequested.getAndSet(true)) {
            LOG.debug("Update scheduled")
            scheduleUpdate()
        } else {
            LOG.debug("Ignoring update as have scheduled")
        }
    }

    private fun scheduleUpdate() {
        scheduledFuture = scheduler.schedule(updateRunnable, delay, TimeUnit.MILLISECONDS)
    }

    protected open fun actionInvocationDone() {
        fireActionFinished()
    }

    protected fun fireActionFinished() {
        LOG.debug("Update is done")
        if (isUpdateRequested.compareAndSet(true, false)) {
            LOG.debug("Someone requested update, rescheduled")
            scheduleUpdate()
        } else {
            LOG.debug("No updates are requested right now")
        }
    }

    override fun dispose() {
        scheduledFuture?.cancel(true)
        scheduledFuture = null
        scheduler.shutdown()
    }

    companion object {
        val LOG = thisLogger()
    }
}
