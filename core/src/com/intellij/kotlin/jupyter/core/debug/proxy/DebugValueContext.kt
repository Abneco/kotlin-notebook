// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy

import com.intellij.debugger.engine.DebugProcessImpl
import com.sun.jdi.ObjectReference

/**
 * Represents current operational context of [objectReference] inside
 * [debugProcess].
 */
data class DebugValueContext(
    val debugProcess: DebugProcessImpl,
    val objectReference: ObjectReference
)
