// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.util.progress.getMaybeCancellable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Designed to be used as a parent scope for all unbounded coroutines in a plugin.
 * Do not use GlobalScope or any other coroutine scope that has an unbounded lifetime.
 *
 *
 * If you have a parent scope such as Project-level service, use [CoroutineScope.childScope] instead.
 */
internal sealed class KotlinNotebookPluginScope : CoroutineScope, Disposable {
    override val coroutineContext: CoroutineContext =
        SupervisorJob() + CoroutineName(javaClass.name)

    override fun dispose() {
        cancel("Disposed ${javaClass.simpleName}")
    }

    /**
     * Schedule and waits for execution of the [block] inside current coroutine.
     * Note that job may be canceled.
     *
     * @param timeOut - timeout in milliseconds
     */
    inline fun <T> invokeAndWait(timeOut: Long?, crossinline action: (CoroutineScope).() -> T?): T? {
        val future = async {
            action()
        }.asCompletableFuture()

        return when (timeOut) {
            null -> future.getMaybeCancellable()
            else -> runBlockingMaybeCancellable {
                future.get(timeOut, TimeUnit.MILLISECONDS)
            }
        }
    }

    companion object {
        val global: KotlinNotebookPluginScope get() = service<GlobalScopeService>()

        fun getForProject(project: Project): KotlinNotebookPluginScope =
            project.service<ProjectScope>()

        @Service
        private class GlobalScopeService : KotlinNotebookPluginScope()

        @Service(Service.Level.PROJECT)
        private class ProjectScope(project: Project) : KotlinNotebookPluginScope()
    }
}

/**
 * Makes this coroutine to be invoked with minimum delay.
 * Suspension points inside the [action] will be executed in the same thread
 * stated inside the [context].
 *
 */
internal fun CoroutineScope.invokeNow(
    context: CoroutineContext = EmptyCoroutineContext,
    action: suspend CoroutineScope.() -> Unit
): Job = launch(context, CoroutineStart.UNDISPATCHED, action)