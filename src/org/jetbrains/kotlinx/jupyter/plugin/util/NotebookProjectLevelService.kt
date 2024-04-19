// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.namedChildScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import java.util.concurrent.ConcurrentHashMap


abstract class NotebookPerFileChildService(
    protected open val virtualFile: BackedNotebookVirtualFile,
    protected val coroutineScope: CoroutineScope
) : Disposable {
    override fun dispose() {
        coroutineScope.cancel()
    }
}

abstract class NotebookProjectLevelService<Child : NotebookPerFileChildService>(
    protected val coroutineScope: CoroutineScope
): Disposable {
    protected val mapping: MutableMap<VirtualFile, Child> = ConcurrentHashMap()

    protected abstract fun createInstance(
        virtualFile: BackedNotebookVirtualFile,
        childScope: CoroutineScope
    ): Child

    fun getOrCreate(virtualFile: BackedNotebookVirtualFile): Child {
        return mapping.getOrPut(virtualFile.file) {
            createInstance(
                virtualFile,
                coroutineScope.namedChildScope(
                    "Child scope for ${virtualFile.file.name} of service ${this::class.simpleName}"
                )
            )
        }
    }

    override fun dispose() {
        coroutineScope.cancel()
        mapping.forEach { Disposer.dispose(it.value) }
    }
}