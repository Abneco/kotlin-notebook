// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiProxyApiExtension
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.JdiProxyFieldAccessorsInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.extensions.notebook.state.JdiVariableStateExtensionHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.state.VariableStateJdiProxy
import com.sun.jdi.ObjectReference
import java.lang.reflect.Method

/**
 * Specialized [java.lang.reflect.InvocationHandler] for invoking custom methods on [VariableStateJdiProxy].
 */
internal class VariableStateJdiProxyInvocationHandler(
    debugProcess: DebugProcessImpl,
    objectReference: ObjectReference,
    override val extensionHandler: JdiProxyApiExtension = JdiVariableStateExtensionHandler(objectReference),
) : JdiProxyFieldAccessorsInvocationHandler(debugProcess, objectReference, extensionHandler)
