// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.messages

import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallback
import com.intellij.openapi.util.Disposer

/**
 * An abstract callback class that ensures proper finalization of kernel message handling while preserving
 * external finalization logic. This class is used to wrap a provided finalization logic (`myFinalizeCallback`)
 * while allowing an additional external callback (`externalFinalizeCallback`) to be defined and executed.
 *
 * Used primarily in the context of Jupyter kernel message handling to ensure cleanup actions are performed
 * regardless of external callback logic, thus preserving the intended finalization workflow.
 *
 * @param myFinalizeCallback The internal finalization logic to be executed after any external callback logic.
 */
abstract class FinalizationPreservingCallback(
    private val myFinalizeCallback : () -> Unit,
): JupyterExecutionCallback {
    private var externalFinalizeCallback: () -> Unit = {}

    init {
        Disposer.register(this) {
            externalFinalizeCallback()
            myFinalizeCallback()
        }
    }
}
