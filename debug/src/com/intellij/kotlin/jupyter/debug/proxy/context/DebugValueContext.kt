// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.context

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi.ObjectReference

/**
 * Represents current operational context of [objectReference] inside
 * [debugProcess].
 *
 * Optional [genericType] when this context represents
 * a generic collection or other parameterized type (element type hint).
 */
data class DebugValueContext(
    val debugProcess: DebugProcessImpl,
    val objectReference: ObjectReference,
    val evaluationContext: EvaluationContextImpl? = null,
    val genericType: Class<*>? = null,
)