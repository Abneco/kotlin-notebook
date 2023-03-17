package org.jetbrains.kotlinx.jupyter.plugin.util

operator fun <T1, T2> com.intellij.openapi.util.Pair<T1, T2>.component1(): T1 {
    return getFirst()
}

operator fun <T1, T2> com.intellij.openapi.util.Pair<T1, T2>.component2(): T2 {
    return getSecond()
}

fun <T> MutableCollection<T>.trimToSize(boundSize: Int, unneededElements: Collection<T>? = null): MutableCollection<T> {
    if (size < boundSize) {
        this.clear()
        return this
    }
    val l = take(boundSize).toSet()
    this.removeIf { it in l || unneededElements?.let { c -> it in c } == true }
    return this
}
