// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.descriptor.api

import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.diagnostic.thisLogger

interface KotlinNotebookValueDescriptor {
    companion object {
        val LOG = thisLogger()
    }


    val debuggerSession: DebuggerSession
}