// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.handlers.delegates

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.DebugValueContext
import com.intellij.kotlin.jupyter.core.debug.proxy.conversion.convertFromJdiValue
import com.intellij.kotlin.jupyter.core.debug.proxy.conversion.convertToJdiValue
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.collections.JdiMapDelegateHandler
import com.intellij.kotlin.jupyter.core.debug.util.getFieldValueByName
import com.sun.jdi.IntegerValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.PrimitiveValue
import com.sun.jdi.StringReference
import com.sun.jdi.Value

/**
 * Implementation for LinkedHashMap.
 * Traverses entries using the internal linked list structure (head/tail fields).
 *
 * Structure:
 * - head: Entry<K, V> - first entry
 * - tail: Entry<K, V> - last entry
 * - Each Entry has: key, value, before, after fields
 *
 * This allows retrieving all entries by traversing from head to tail
 * without invoking remote methods.
 *
 */
internal class JdiMapDelegateExtensionHandlerImpl(
    valueContext: DebugValueContext,
) : JdiMapDelegateHandler {
    override val debugProcess: DebugProcessImpl = valueContext.debugProcess
    override val objectReference: ObjectReference = valueContext.objectReference

    /**
     * Traverses entries using head -> after -> ... -> tail structure
     */
    private fun traverseEntries(): List<EntryData> {
        val entries = mutableListOf<EntryData>()

        // Get head entry
        var currentEntry: ObjectReference? = objectReference.getFieldValueByName("head") as? ObjectReference

        // Loop through a linked list
        while (currentEntry != null) {
            val key = currentEntry.getFieldValueByName("key")
            val value = currentEntry.getFieldValueByName("value")

            entries.add(EntryData(key, value))

            currentEntry = currentEntry.getFieldValueByName("after") as? ObjectReference
        }

        return entries
    }

    override val size: Int
        get() {
            val sizeValue = objectReference.getFieldValueByName("size")
            return (sizeValue as? IntegerValue)?.value() ?: 0
        }

    override fun isEmpty(): Boolean = size == 0

    override val entries: Set<Map.Entry<Any?, Any?>>
        get() = traverseEntries().map {
            SimpleEntry(
                convertJdiValue(it.key),
                convertJdiValue(it.value)
            )
        }.toSet()

    override val keys: Set<Any?>
        get() = traverseEntries().map { convertJdiValue(it.key) }.toSet()

    override val values: Collection<Any?>
        get() = traverseEntries().map { convertJdiValue(it.value) }

    override fun get(key: Any?): Any? {
        if (key == null) return null
        val entries = traverseEntries()
        val jdiKey = convertToJdiValue(key)

        return entries.firstOrNull { areEqual(it.key, jdiKey) }?.let {
            convertJdiValue(it.value)
        }
    }

    override fun containsKey(key: Any?): Boolean {
        if (key == null) return false
        val jdiKey = convertToJdiValue(key)
        return traverseEntries().any { areEqual(it.key, jdiKey) }
    }

    override fun containsValue(value: Any?): Boolean {
        if (value == null) return false
        val jdiValue = convertToJdiValue(value)
        return traverseEntries().any { areEqual(it.value, jdiValue) }
    }

    private fun convertJdiValue(value: Value?): Any? {
        return value.convertFromJdiValue(debugProcess)
    }

    private fun convertToJdiValue(value: Any): Value {
        val vm = objectReference.virtualMachine()
        return value.convertToJdiValue(vm)
    }

    private fun areEqual(jdiValue1: Value?, jdiValue2: Value?): Boolean {
        if (jdiValue1 == null && jdiValue2 == null) return true
        if (jdiValue1 == null || jdiValue2 == null) return false

        return when (jdiValue1) {
            is StringReference if jdiValue2 is StringReference ->
                jdiValue1.value() == jdiValue2.value()
            is PrimitiveValue if jdiValue2 is PrimitiveValue ->
                convertJdiValue(jdiValue1) == convertJdiValue(jdiValue2)
            is ObjectReference if jdiValue2 is ObjectReference ->
                jdiValue1.uniqueID() == jdiValue2.uniqueID()
            else -> false
        }
    }

    private data class EntryData(val key: Value?, val value: Value?)

    /**
     * Simple Map.Entry implementation for returned entries
     */
    private class SimpleEntry<K, V>(
        override val key: K,
        override val value: V
    ) : Map.Entry<K, V>
}