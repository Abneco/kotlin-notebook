package org.jetbrains.kotlinx.jupyter.plugin.util

operator fun <T1, T2> com.intellij.openapi.util.Pair<T1, T2>.component1(): T1 {
    return getFirst()
}

operator fun <T1, T2> com.intellij.openapi.util.Pair<T1, T2>.component2(): T2 {
    return getSecond()
}
