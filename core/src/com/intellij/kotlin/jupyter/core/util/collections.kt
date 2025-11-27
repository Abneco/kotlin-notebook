package com.intellij.kotlin.jupyter.core.util

import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.filterIsInstanceAndTo

operator fun <T1, T2> com.intellij.openapi.util.Pair<T1, T2>.component1(): T1 {
    return getFirst()
}

operator fun <T1, T2> com.intellij.openapi.util.Pair<T1, T2>.component2(): T2 {
    return getSecond()
}

inline fun <reified R> Sequence<*>.firstOfType(): R? = filterIsInstance<R>().firstOrNull()

fun <T, R> Collection<T>.buildFlatMap(appender: MutableList<R>.(T) -> Unit): List<R> {
    if (isEmpty()) return emptyList()

    val originalCollection = this
    return buildList {
        for (element in originalCollection) {
            appender(element)
        }
    }
}

inline fun <reified R> Collection<*>.filterIsInstanceAnd(predicate: (R) -> Boolean): List<R> {
    if (isEmpty()) return emptyList()
    return filterIsInstanceAndTo(SmartList(), predicate)
}
