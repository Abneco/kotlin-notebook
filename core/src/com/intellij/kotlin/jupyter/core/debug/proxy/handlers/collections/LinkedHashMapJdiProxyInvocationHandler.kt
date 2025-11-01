// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.handlers.collections

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiProxyApiExtension
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.JdiProxyFieldAccessorsInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.extensions.JdiMapExtensionHandlerImpl
import com.sun.jdi.ObjectReference

/**
 * Specialized InvocationHandler for LinkedHashMap that efficiently traverses entries
 * using the internal linked list structure (head/tail fields).
 *
 * Structure:
 * - head: Entry<K, V> - first entry
 * - tail: Entry<K, V> - last entry
 * - Each Entry has: key, value, before, after fields
 *
 * This allows retrieving all entries by traversing from head to tail
 * without invoking remote methods.
 */
internal class LinkedHashMapJdiProxyInvocationHandler(
    debugProcess: DebugProcessImpl,
    linkedHashMapRef: ObjectReference,
    override val extensionHandler: JdiProxyApiExtension = JdiMapExtensionHandlerImpl(debugProcess, linkedHashMapRef)
) : JdiProxyFieldAccessorsInvocationHandler(debugProcess, linkedHashMapRef, extensionHandler)
