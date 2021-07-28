package org.jetbrains.kotlinx.jupyter.plugin.util

/**
 * This list implementation may be used for debugging [ConcurrentModificationException] and for other purposes
 */
class LoggingList<T>(private val delegate: MutableList<T> = mutableListOf()) : MutableList<T> by delegate {
    private val log = LogSaver()

    override fun add(element: T): Boolean {
        log("Adding $element, size: ${delegate.size}")
        val res = delegate.add(element)
        log("Added: $element, size: ${delegate.size}")
        return res
    }

    override fun clear() {
        log("Clear requested, size: ${delegate.size}")
        delegate.clear()
    }

    override fun iterator(): MutableIterator<T> {
        log("Iterator requested, size: ${delegate.size}")
        return LoggingIterator(delegate.iterator())
    }

    private inner class LoggingIterator<T>(private val delegate: MutableIterator<T>) : MutableIterator<T> by delegate {
        override fun next(): T {
            log("Calling next() on iterator")
            return delegate.next()
        }
    }
}
