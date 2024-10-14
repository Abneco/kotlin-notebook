// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.ui.content.Content
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.util.*
import javax.swing.SwingUtilities

fun uiFeelsDark(): Boolean {
    return StartupUiUtil.isDarkTheme
}

fun Component.ancestors() = generateSequence(this) { it.parent }

inline fun <reified T> Component.firstAncestorOfType() = ancestors().firstOfType<T>()

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
inline fun <reified T> Component.firstDescendantOfType() = dfsDescendants().firstOfType<T>()

fun interface MouseEventDispatcher {
    fun dispatch(event: MouseEvent?)
}

/**
 * Redispatches passed mouse event to the deepest subcomponent of [newTarget] if the event occurs above [newTarget]
 */
class MouseEventDeepReDispatcher(
    private val newTarget: Component,
    private val eventFilter: (e: MouseEvent) -> Boolean = { true },
): MouseEventDispatcher {
    override fun dispatch(event: MouseEvent?) {
        if (event == null || !eventFilter(event)) return
        val newTargetEvent = SwingUtilities.convertMouseEvent(event.component, event, newTarget)
        val deepestChild = SwingUtilities.getDeepestComponentAt(newTarget, newTargetEvent.x, newTargetEvent.y) ?: return
        val deepestChildEvent = SwingUtilities.convertMouseEvent(newTargetEvent.component, newTargetEvent, deepestChild)
        deepestChild.dispatchEvent(deepestChildEvent)

    }
}

fun Component.addDispatchingMouseListener(dispatcher: MouseEventDispatcher) {
    val mouseMotionListener = object : MouseMotionListener {
        override fun mouseDragged(e: MouseEvent?) {
            dispatcher.dispatch(e)
        }

        override fun mouseMoved(e: MouseEvent?) {
            dispatcher.dispatch(e)
        }
    }

    val mouseListener = object : MouseListener {
        override fun mouseClicked(e: MouseEvent?) {
            dispatcher.dispatch(e)
        }

        override fun mousePressed(e: MouseEvent?) {
            dispatcher.dispatch(e)
        }

        override fun mouseReleased(e: MouseEvent?) {
            dispatcher.dispatch(e)
        }

        override fun mouseEntered(e: MouseEvent?) {
            dispatcher.dispatch(e)
        }

        override fun mouseExited(e: MouseEvent?) {
            dispatcher.dispatch(e)
        }
    }

    addMouseMotionListener(mouseMotionListener)
    addMouseListener(mouseListener)
}

abstract class CursorProvider(protected val component: Component) {
    abstract fun provideCursor(x: Int, y: Int): Cursor?

    fun interface Factory {
        fun create(component: Component): CursorProvider
    }
}

class RetargetingCursorProvider(component: Component, private val boundsSource: Component): CursorProvider(component) {
    override fun provideCursor(x: Int, y: Int): Cursor? {
        val pointWithinSource = SwingUtilities.convertPoint(component, x, y, boundsSource)
        val cursorSource = SwingUtilities.getDeepestComponentAt(boundsSource, pointWithinSource.x, pointWithinSource.y) ?: return null
        return cursorSource.cursor
    }

    class Factory(private val boundsSource: Component): CursorProvider.Factory {
        override fun create(component: Component): CursorProvider {
            return RetargetingCursorProvider(component, boundsSource)
        }
    }
}

fun Component.addCursorProvider(cursorProviderFactory: CursorProvider.Factory) {
    val component = this
    val defaultCursor = component.cursor
    val cursorProvider = cursorProviderFactory.create(component)

    fun updateComponentCursor(e: MouseEvent?) {
        if (e == null) return
        val newCursor = cursorProvider.provideCursor(e.x, e.y)
        UIUtil.setCursor(component, newCursor ?: defaultCursor)
    }

    val mouseMotionListener = object : MouseMotionListener {
        override fun mouseDragged(e: MouseEvent?) {
        }

        override fun mouseMoved(e: MouseEvent?) {
            updateComponentCursor(e)
        }
    }

    addMouseMotionListener(mouseMotionListener)
}

fun RunnerLayoutUi.addNotebookTabsContent(vararg contents: Content?) {
    for (content in contents) {
        if (content == null) continue
        content.isCloseable = false
        addContent(content)
    }
}