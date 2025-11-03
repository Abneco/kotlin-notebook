// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.JdiProxyCompoundInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.NotebookJdiProxy
import com.intellij.kotlin.jupyter.core.debug.util.isOfTypeByName
import com.sun.jdi.ObjectReference
import java.lang.reflect.Proxy

/**
 * Creates a dynamic proxy for a JDI [objectReference] based on a class [T].
 *
 * This allows type-safe access to remote objects by implementing their interfaces.
 * Methods are delegated to the actual JDI object using invokeMethod() or field access.
 *
 * Example:
 * val mapRef: ObjectReference = ...
 * val map: Map<String, Any> = createJdiObjectProxy(mapRef)
 * val size = map.size // Invokes size() method or accesses size field on a remote object
 *
 */
internal inline fun <reified T> createJdiObjectProxy(
    debugProcess: DebugProcessImpl,
    objectReference: ObjectReference
): T {
    @Suppress("UNCHECKED_CAST")
    return createJdiObjectProxy(
        debugProcess, objectReference, T::class.java
    ) as T
}

internal fun createJdiObjectProxy(
    debugProcess: DebugProcessImpl,
    objectReference: ObjectReference,
    type: Class<*>
): Any {
    require(type.isInterface) {
        "Type parameter T must be an interface, got ${type.name}"
    }
    val handler = JdiProxyCompoundInvocationHandler(
        DebugValueContext(debugProcess, objectReference)
    )

    return Proxy.newProxyInstance(
        JdiObjectReferenceProxy::class.java.classLoader,
        arrayOf(type, JdiObjectReferenceProxy::class.java),
        handler
    )
}

internal fun ObjectReference.isLinkedHashMap(): Boolean {
    val refType = referenceType()
    return refType.isOfTypeByName<LinkedHashMap<*, *>>() ||
            refType.name().startsWith("java.util.LinkedHashMap$")
}

internal fun ObjectReference.isNotebookProxy(): Boolean {
    val refType = referenceType()
    return refType.isOfTypeByName<NotebookJdiProxy>()
}