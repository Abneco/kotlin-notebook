package org.jetbrains.kotlinx.jupyter.plugin.util

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
