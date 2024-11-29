// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.util.concurrent.ConcurrentHashMap

/**
 * Representation of a per-file notebook service.
 *
 * This class provides a base implementation for services related to a specific notebook file.
 * [CoroutineScope] is passed from the parent project-level service to handle async operations.
 *
 * @see NotebookProjectLevelService
 */
abstract class NotebookPerFileChildService(
    protected open val virtualFile: BackedNotebookVirtualFile,
    protected val coroutineScope: CoroutineScope
) : Disposable {
    override fun dispose() {
        coroutineScope.cancel()
    }
}

/**
 * Abstract class representing a notebook project-level service.
 * Every such class contains a collection of per-file [Child] services instantiated by a demand.
 *
 * Note that for every [Child], its own [CoroutineScope] is created as a child scope of 'this.coroutineScope'.
 */
abstract class NotebookProjectLevelService<Child : NotebookPerFileChildService>(
    val project: Project,
    protected val coroutineScope: CoroutineScope
) : Disposable {
    protected val mapping: MutableMap<VirtualFile, Child> = ConcurrentHashMap()

    /**
     * Creates a new [Child] service for the given [virtualFile]
     * with its own [CoroutineScope].
     */
    protected abstract fun createInstance(
        virtualFile: BackedNotebookVirtualFile,
        fileScope: CoroutineScope
    ): Child

    fun getOrCreate(backedFile: BackedNotebookVirtualFile): Child {
        return mapping.getOrPut(backedFile.file) {
            addDisposableChild(
                createInstance(
                    backedFile,
                    coroutineScope.childScope(
                        "Child scope for ${backedFile.file.name} of service ${this::class.simpleName}"
                    )
                )
            )
        }
    }

    fun remove(backedFile: BackedNotebookVirtualFile) {
        mapping.remove(backedFile.file)?.let { child ->
            Disposer.dispose(child)
        }
    }

    override fun dispose() {
        coroutineScope.cancel()
        mapping.clear()
    }
}

/**
 * docs
 */
internal inline fun <T : Disposable> Disposable.createDisposableChild(crossinline factory: () -> T): T {
    return addDisposableChild(factory())
}

internal fun <T : Disposable> Disposable.addDisposableChild(child: T): T {
    Disposer.register(this, child)
    return child
}