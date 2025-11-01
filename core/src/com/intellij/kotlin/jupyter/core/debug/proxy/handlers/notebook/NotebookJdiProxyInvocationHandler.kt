// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.handlers.notebook

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiProxyApiExtension
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.JdiProxyFieldAccessorsInvocationHandler
import com.intellij.kotlin.jupyter.core.debug.proxy.handlers.extensions.notebook.JdiNotebookExtensionHandler
import com.sun.jdi.ObjectReference

/**
 * Specialized [java.lang.reflect.InvocationHandler] for [com.intellij.kotlin.jupyter.core.debug.proxy.notebook.NotebookJdiProxy] that delegates to base handler
 * but provides custom logic for the variablesHolderProxy accessor.
 *
 * Special methods are typically made in a way that values are wrapped in a proxy to allow JDI-like access to remote objects.
 */
internal class NotebookJdiProxyInvocationHandler(
    debugProcess: DebugProcessImpl,
    objectReference: ObjectReference,
    override val extensionHandler: JdiNotebookExtensionHandler = JdiNotebookExtensionHandler(debugProcess, objectReference),
) : JdiProxyFieldAccessorsInvocationHandler(debugProcess, objectReference, extensionHandler)
