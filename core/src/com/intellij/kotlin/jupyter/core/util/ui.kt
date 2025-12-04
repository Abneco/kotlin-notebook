// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.util.ui.StartupUiUtil
import java.awt.Component
import java.awt.Container
import java.util.*

fun uiFeelsDark(): Boolean {
    return StartupUiUtil.isDarkTheme
}

fun Component.ancestors(): Sequence<Component> = generateSequence(this) { it.parent }

inline fun <reified T> Component.firstAncestorOfType(): T? = ancestors().firstOfType<T>()

fun Component.dfsDescendants(): Sequence<Component> {
    return sequence {
        val queue: Queue<Component> = LinkedList()
        queue.add(this@dfsDescendants)

        while (queue.isNotEmpty()) {
            val component = queue.poll()
            yield(component)

            if (component is Container) {
                for (child in component.components) {
                    queue.add(child)
                }
            }
        }
    }
}

@Suppress("UNUSED")
inline fun <reified T> Component.firstDescendantOfType(): T? = dfsDescendants().firstOfType<T>()
