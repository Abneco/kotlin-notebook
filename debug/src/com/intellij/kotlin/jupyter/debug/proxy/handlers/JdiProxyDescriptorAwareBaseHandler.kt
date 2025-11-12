// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.handlers

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.kotlin.jupyter.debug.proxy.JdiDescriptorAwareProxy
import com.intellij.kotlin.jupyter.debug.proxy.context.DebugValueContext
import com.intellij.kotlin.jupyter.debug.proxy.providers.NotebookInternalStateHandlersProvider
import com.sun.jdi.ObjectReference

/**
 * Base implementation for Jdi proxy, which can manipulate
 * its [JavaValue].
 *
 * This class is used as a fallback choice inside [NotebookInternalStateHandlersProvider]
 */
open class JdiProxyDescriptorAwareBaseHandler(
    valueContext: DebugValueContext,
) : JdiDescriptorAwareProxy {
    private val evaluationContext by lazy { valueContext.evaluationContext }

    @Volatile
    private var _javaValue: JavaValue? = null

    override val debugProcess: DebugProcessImpl = valueContext.debugProcess
    override val objectReference: ObjectReference = valueContext.objectReference

    override val javaValue: JavaValue?
        get() = _javaValue

    override fun bindDescriptor(valueDescriptor: ValueDescriptorImpl) {
        val currentValue = _javaValue
        if (currentValue?.descriptor == valueDescriptor) {
            return
        }

        _javaValue = computeJavaValue(valueDescriptor)
    }

    /**
     * Binds an externally created [JavaValue] to this proxy without recreating it.
     */
    final override fun bindValueFromRuntime(value: JavaValue?) {
        _javaValue = value
    }

    private fun computeJavaValue(valueDescriptor: ValueDescriptorImpl): JavaValue? {
        val nodeManager = debugProcess.xdebugProcess?.nodeManager ?: return null
        val context = evaluationContext ?: return null

        val javaValue = JavaValue.create(
            null,
            valueDescriptor,
            context,
            nodeManager,
            false
        )

        javaValue.descriptor.setContext(context)
        return javaValue
    }
}
