// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.fasterxml.jackson.databind.JsonNode
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.plugin.util.toJacksonJson
import org.jetbrains.plugins.notebooks.jackson
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessageBase
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessageChannel
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterProtocolSchemaFactory

fun createZMQJupyterMessage(channel: JupyterMessageChannel, rawMessage: RawMessage): JupyterMessage {
    val schema = JupyterProtocolSchemaFactory.createSchema()
    val json = jackson.createObjectNode().apply {
        put(schema.channelFieldsName, channel.value)
        set<JsonNode>(schema.headerFieldName, rawMessage.header.toJacksonJson())
        set<JsonNode>(schema.parentHeaderFieldName, rawMessage.parentHeader?.toJacksonJson())
        set<JsonNode>("metadata", rawMessage.metadata?.toJacksonJson())
        set<JsonNode>(schema.contentFieldName, rawMessage.content.toJacksonJson())
    }


    return JupyterMessageBase(
        json,
        rawMessage.id,
        schema
    )
}
