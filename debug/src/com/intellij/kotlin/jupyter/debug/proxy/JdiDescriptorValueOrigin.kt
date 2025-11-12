// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy

/**
 * Describes origin of a [com.sun.jdi.ObjectReference] for which
 * a particular [com.intellij.debugger.ui.tree.ValueDescriptor] was created.
 */
enum class JdiDescriptorValueOrigin {
    FieldValue,
    MethodEvaluation
}