// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * Designed to be used as a parent scope for all unbounded coroutines in the Notebook plugin.
 * Do not use GlobalScope or any other coroutine scope that has an unbounded lifetime.
 *
 * If you have a parent scope such as Project-level service, use [childScope] instead.
 */
sealed class KotlinNotebookPluginScope : CoroutineScope, Disposable {
    override val coroutineContext: CoroutineContext =
        SupervisorJob() + CoroutineName(javaClass.name)

    override fun dispose() {
        cancel("Disposed ${javaClass.simpleName}")
    }

    /**
     * Schedule and waits for execution of the [action] inside current coroutine.
     * Note that a job may be canceled.
     *
     * @param timeout - timeout in milliseconds
     * @param onError - logic for handling errors, including the operation timing out.
     */
    inline fun <T> invokeAndWait(
        timeout: Duration? = null,
        crossinline action: suspend (CoroutineScope).() -> T?,
        crossinline onError: (Throwable) -> Unit = { },
    ): T? {
        val deferred = async {
            action()
        }.apply {
            invokeOnCompletion { throwable ->
                if (throwable != null) {
                    onError(throwable)
                }
            }
        }

        return runBlockingMaybeCancellable {
            when(timeout) {
                null -> deferred.await()
                else -> withTimeout(timeout) { deferred.await() }
            }
        }
    }

    /**
     * Schedule and waits for execution of the [action] inside current coroutine.
     * Note that a job may be canceled.
     * Throws an exception if it happened inside [action].
     *
     * @param timeout - timeout in milliseconds
     */
    inline fun <T> invokeAndWait(
        timeout: Duration? = null,
        crossinline action: suspend (CoroutineScope).() -> T?
    ): T? {
        return invokeAndWait(timeout, action) { error ->
            throw error
        }
    }

    companion object {
        /**
         * Schedules a coroutine to run on the Event Dispatch Thread.
         */
        fun invokeOnEDT(action: suspend CoroutineScope.() -> Unit): Job =
            global.async(Dispatchers.EDT, block = action)

        /**
         * Retrieves global coroutine scope for the Kotlin Notebook plugin.
         * Designed to be used in places where the project scope is not available.
         */
        val global: KotlinNotebookPluginScope get() = service<GlobalScopeService>()

        /**
         * Retrieves project-level scope for the Kotlin Notebook plugin.
         * Designed to be used for any asynchronous work where [Project] is accessible.
         */
        fun getForProject(project: Project): KotlinNotebookPluginScope =
            project.service<ProjectScope>()

        @Service
        private class GlobalScopeService : KotlinNotebookPluginScope()

        @Service(Service.Level.PROJECT)
        private class ProjectScope : KotlinNotebookPluginScope()
    }
}
