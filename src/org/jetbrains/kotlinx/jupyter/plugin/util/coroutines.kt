// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.progress.getMaybeCancellable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.asCompletableFuture
import kotlin.coroutines.CoroutineContext

/**
 * Designed to be used as a parent scope for all unbounded coroutines in a plugin.
 * Do not use GlobalScope or any other coroutine scope that has an unbounded lifetime.
 *
 *
 * If you have a parent scope such as Project-level service, use [CoroutineScope.childScope] instead.
 */
internal sealed class KotlinNotebookGlobalScope : CoroutineScope, Disposable {
    override val coroutineContext: CoroutineContext =
        SupervisorJob() + CoroutineName(javaClass.name)

    override fun dispose() {
        cancel("Disposed ${javaClass.simpleName}")
    }

    /**
     * Schedule and waits for execution of the [block] inside current coroutine.
     * Note that job may be canceled.
     */
    inline fun <T> invokeAndWait(crossinline action: (CoroutineScope).() -> T?): T? {
        return async {
            action()
        }.asCompletableFuture()
        .getMaybeCancellable()
    }

    companion object {
        val global: KotlinNotebookGlobalScope get() = service<GlobalScopeService>()

        fun getForProject(project: Project): KotlinNotebookGlobalScope =
            project.service<ProjectScope>()

        @Service
        private class GlobalScopeService : KotlinNotebookGlobalScope()

        @Service(Service.Level.PROJECT)
        private class ProjectScope(project: Project) : KotlinNotebookGlobalScope()
    }
}