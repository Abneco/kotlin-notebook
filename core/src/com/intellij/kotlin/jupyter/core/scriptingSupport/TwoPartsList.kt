// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

/**
 * A list that maintains two separate parts: initial (kernel) and snippet (user) dependencies.
 *
 * Thread safety: all access must be externally synchronized (e.g., via `dataLock` in [JupyterCompilerPerFileService]).
 */
class TwoPartsList<T>(
    private val initialPart: MutableSet<T> = mutableSetOf(),
    private val snippetsPart: MutableSet<T> = mutableSetOf(),
) {
    val size: Int get() = initialPart.size + snippetsPart.size

    val hasInitialPart: Boolean get() = initialPart.isNotEmpty()

    fun clear() {
        snippetsPart.clear()
    }

    fun addInitial(items: Collection<T>) {
        initialPart.addAll(items)
    }

    fun addSnippet(items: Collection<T>) {
        snippetsPart.addAll(items)
    }

    fun removeSnippet(items: Collection<T>) {
        snippetsPart.removeAll(items)
    }

    fun getList(): List<T> {
        return (initialPart + snippetsPart).distinct()
    }
}
