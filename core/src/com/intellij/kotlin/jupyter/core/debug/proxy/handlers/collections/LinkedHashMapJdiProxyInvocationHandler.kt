// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.handlers.collections

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiObjectReferenceProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.conversion.convertFromJdiValue
import com.intellij.kotlin.jupyter.core.debug.proxy.createJdiObjectProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.JdiProxyFieldAccessorsInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.isLinkedHashMap
import com.intellij.kotlin.jupyter.core.debug.util.getFieldValueByName
import com.sun.jdi.BooleanValue
import com.sun.jdi.ByteValue
import com.sun.jdi.CharValue
import com.sun.jdi.DoubleValue
import com.sun.jdi.FloatValue
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.PrimitiveValue
import com.sun.jdi.ShortValue
import com.sun.jdi.StringReference
import com.sun.jdi.Value
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Specialized InvocationHandler for LinkedHashMap that efficiently traverses entries
 * using the internal linked list structure (head/tail fields).
 *
 * Structure:
 * - head: Entry<K,V> - first entry
 * - tail: Entry<K,V> - last entry
 * - Each Entry has: key, value, before, after fields
 *
 * This allows retrieving all entries by traversing from head to tail
 * without invoking remote methods.
 */
internal class LinkedHashMapJdiProxyInvocationHandler(
  debugProcess: DebugProcessImpl,
  linkedHashMapRef: ObjectReference
) : JdiProxyFieldAccessorsInvocationHandler(debugProcess, linkedHashMapRef) {

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        return when (method.name) {
            "size" -> getSize()
            "isEmpty" -> getSize() == 0
            "entrySet", "entries" -> getEntries()
            "keySet", "keys" -> getKeys()
            "values" -> getValues()
            "get" -> get(args?.firstOrNull())
            "containsKey" -> containsKey(args?.firstOrNull())
            "containsValue" -> containsValue(args?.firstOrNull())
            "getObjectReference" -> objectReference
            else -> throw UnsupportedOperationException("Method ${method.name} is not supported for LinkedHashMap proxy")
        }
    }

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

    private fun getSize(): Int {
        val sizeValue = objectReference.getFieldValueByName("size")
        return (sizeValue as? IntegerValue)?.value() ?: 0
    }

    private fun getEntries(): Set<Map.Entry<Any?, Any?>> {
        return traverseEntries().map {
            SimpleEntry(
                convertJdiValue(it.key),
                convertJdiValue(it.value)
            )
        }.toSet()
    }

    private fun getKeys(): Set<Any?> {
        return traverseEntries().map { convertJdiValue(it.key) }.toSet()
    }

    private fun getValues(): Collection<Any?> {
        return traverseEntries().map { convertJdiValue(it.value) }
    }

    private fun get(key: Any?): Any? {
        if (key == null) return null
        val entries = traverseEntries()
        val jdiKey = convertToJdiValue(key)

        return entries.firstOrNull { areEqual(it.key, jdiKey) }?.let {
            convertJdiValue(it.value)
        }
    }

    private fun containsKey(key: Any?): Boolean {
        if (key == null) return false
        val jdiKey = convertToJdiValue(key)
        return traverseEntries().any { areEqual(it.key, jdiKey) }
    }

    private fun containsValue(value: Any?): Boolean {
        if (value == null) return false
        val jdiValue = convertToJdiValue(value)
        return traverseEntries().any { areEqual(it.value, jdiValue) }
    }

    private fun convertJdiValue(value: Value?): Any? {
        return value.convertFromJdiValue(debugProcess)
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