// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.concurrency.ConcurrentCollectionFactory

class DoubleKeyMutableMap<K1: Any, K2: Any, V: Any>(
    private val map1: MutableMap<K1, V>,
    private val map2: MutableMap<K2, V>,
    private val keyExtractor1: (V) -> K1,
    private val keyExtractor2: (V) -> K2,
) {
    fun getByFirstKey(key1: K1) = map1[key1]
    fun getBySecondKey(key2: K2) = map2[key2]

    fun put(value: V) {
        map1[keyExtractor1(value)] = value
        map2[keyExtractor2(value)] = value
    }

    fun removeByFirstKey(key1: K1): Boolean {
        return map1[key1]?.let { removeByValue(it) } ?: false
    }

    fun removeBySecondKey(key2: K2): Boolean {
        return map2[key2]?.let { removeByValue(it) } ?: false
    }

    fun removeByValue(value: V): Boolean {
        val res1 = map1.remove(keyExtractor1(value), value)
        val res2 = map2.remove(keyExtractor2(value), value)
        return res1 || res2
    }

    fun values(): Collection<V> = map1.values

    fun clear() {
        map1.clear()
        map2.clear()
    }
}

fun <K1: Any, K2: Any, V: Any> createConcurrentDoubleKeyMap(
    keyExtractor1: (V) -> K1,
    keyExtractor2: (V) -> K2,
) = DoubleKeyMutableMap(
    ConcurrentCollectionFactory.createConcurrentMap(),
    ConcurrentCollectionFactory.createConcurrentMap(),
    keyExtractor1,
    keyExtractor2,
)
