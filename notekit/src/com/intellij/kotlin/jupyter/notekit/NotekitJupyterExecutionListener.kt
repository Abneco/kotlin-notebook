// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.notekit

import com.intellij.jupyter.core.executor.JupyterExecutionListener
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.openapi.util.Disposer

/**
 * Listens for session creation and registers the Notekit comm target handler.
 *
 * This listener registers a [com.intellij.jupyter.core.jupyter.connections.execution.comms.JupyterCommHandler]
 * for target_name "jupyter.notekit.v1"
 * which intercepts comm messages for notebook manipulation protocol.
 */
class NotekitJupyterExecutionListener : JupyterExecutionListener {
    override suspend fun sessionCreated(session: JupyterNotebookSession) {
        val handler = NotekitCommHandler(session)
        Disposer.register(session, handler)

        // Register the handler with the session client
        session.internalClient.commManager.registerCommHandler(handler)
    }
}
