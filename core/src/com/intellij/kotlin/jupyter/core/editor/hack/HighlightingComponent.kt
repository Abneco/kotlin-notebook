// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack

import com.intellij.kotlin.jupyter.core.util.addDisposableChild
import com.intellij.openapi.Disposable

abstract class HighlightingComponent : Disposable {
    private val children = mutableListOf<HighlightingComponent>()

    fun <T: HighlightingComponent> child(factory: () -> T): T {
        val child = factory()
        children.add(child)
        addDisposableChild(child)
        return child
    }

    protected open fun initializeSelf() {}

    fun initialize() {
        for (child in children) child.initialize()
        initializeSelf()
    }

    override fun dispose() {
        children.clear()
    }
}