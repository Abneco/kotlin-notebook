// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy

import com.sun.jdi.ObjectReference

/**
 * Base proxy wrapper for JDI object, used to get access to [ObjectReference].
 */
interface JdiObjectReferenceProxy {
    val objectReference: ObjectReference
}