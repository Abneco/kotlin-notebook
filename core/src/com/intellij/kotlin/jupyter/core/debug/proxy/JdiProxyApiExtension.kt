// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy

import com.sun.jdi.ObjectReference

/**
 * Marker interface for JDI proxy extension interfaces.
 *
 * Extension interfaces provide additional methods that are not part of the proxied remote object
 * but add functionality specific to the proxy implementation (e.g., accessing runtime context).
 *
 */
interface JdiProxyApiExtension : JdiObjectReferenceProxy


internal class BaseJdiProxyApiExtension(
    override val objectReference: ObjectReference
) : JdiProxyApiExtension
