// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.notekit

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.comms.JupyterCommHandler
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallback
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterCommMessageBuilder
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageId
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageType
import com.intellij.jupyter.core.jupyter.connections.execution.message.createHeader
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles comm messages for the Notekit Protocol.
 * This handler intercepts comm_open messages with target_name "jupyter.notekit.v1"
 * and processes later comm_msg requests.
 */
class NotekitCommHandler(
    private val session: JupyterNotebookSession,
) : JupyterCommHandler, Disposable {

    private val activeComms = ConcurrentHashMap<String, NotekitSession>()
    private val scope get() = session.scope

    val notebookFile: BackedNotebookVirtualFile
        get() = session.virtualFile

    override fun supportsTarget(targetName: String): Boolean {
        return targetName == NotekitProtocol.TARGET_NAME
    }

    /**
     * Called when a comm_open message arrives. Returns true if this handler should process it.
     */
    override fun handleCommOpen(message: JupyterMessage): Boolean {
        val commId = message.messageContent["comm_id"]?.asText() ?: return false
        LOG.info("Notekit comm opened: $commId")

        val notekitSession = NotekitSession(session.project, notebookFile, session)
        activeComms[commId] = notekitSession

        return true
    }

    /**
     * Called when a comm_msg message arrives. Returns true if this handler processed it.
     */
    override fun handleCommMsg(message: JupyterMessage): Boolean {
        val commId = message.messageContent["comm_id"]?.asText() ?: return false
        val notekitSession = activeComms[commId] ?: return false

        val data = message.messageContent["data"] ?: return false
        val request = NotekitRequest.parse(data) ?: run {
            LOG.warn("Failed to parse notekit request: $data")
            return true // We own this comm, but the request was malformed
        }

        scope.launch {
            val response = notekitSession.processRequest(request)
            sendResponse(commId, response, message.header.messageId)
        }

        return true
    }

    /**
     * Called when a comm_close message arrives.
     */
    override fun handleCommClose(message: JupyterMessage): Boolean {
        val commId = message.messageContent["comm_id"]?.asText() ?: return false
        val removed = activeComms.remove(commId)
        if (removed != null) {
            LOG.info("Notekit comm closed: $commId")
            return true
        }
        return false
    }

    override fun ownsComm(commId: String): Boolean {
        return activeComms.containsKey(commId)
    }

    private suspend fun sendResponse(commId: String, response: ObjectNode, parentMessageId: JupyterMessageId) {
        val message = JupyterCommMessageBuilder(
            sessionId = session.sessionId,
            data = response,
            commId = commId,
        ).apply {
            parentHeader = createHeader(session.sessionId, JupyterMessageType.COMM_MSG, messageId = UUID.fromString(parentMessageId.id))
        }.build()

        session.sendMessage(message, MyExecutionListener)
    }

    override fun dispose() {
        activeComms.clear()
        session.internalClient.commManager.unregisterCommHandler(this)
    }

    private object MyExecutionListener: JupyterExecutionCallback

    companion object {
        private val LOG: Logger = logger<NotekitCommHandler>()
    }
}
