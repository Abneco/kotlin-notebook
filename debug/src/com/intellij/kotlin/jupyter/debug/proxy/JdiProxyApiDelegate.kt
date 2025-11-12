// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.kotlin.jupyter.debug.util.isDebuggerManagerThread

/**
 * Marker interface for JDI proxy extension interfaces.
 *
 * Extension interfaces provide additional methods that are not part of the proxied remote object
 * but add functionality specific to the proxy implementation (e.g., accessing runtime context).
 *
 */
interface JdiProxyApiDelegate : JdiObjectReferenceProxy {
    val debugProcess: DebugProcessImpl
}

/**
 * Minimal interface for JDI proxy extension interfaces that provide
 * a [JavaValue] for the proxied remote object.
 */
interface JdiDescriptorAwareProxy : JdiProxyApiDelegate {
    val javaValue: JavaValue?
        get() = null

    /**
     * Rendered text representation from the runtime context.
     */
    val renderedText: String?
        get() = javaValue?.descriptor?.valueText

    /**
     * Sets [com.intellij.debugger.ui.tree.ValueDescriptor] for this proxy.
     * It's required to compute representation and get access
     * to all features available to [com.intellij.xdebugger.frame.XValue].
     *
     * To be invoked in [com.intellij.debugger.engine.DebuggerManagerThreadImpl]
     * @see [isDebuggerManagerThread]
     */
    fun bindDescriptor(valueDescriptor: ValueDescriptorImpl) {}

    /**
     * Binds an externally created [JavaValue] to this proxy without recreating it.
     *
     * Should be invoked in [com.intellij.debugger.engine.DebuggerManagerThreadImpl]
     */
    fun bindValueFromRuntime(value: JavaValue?) {}
}
