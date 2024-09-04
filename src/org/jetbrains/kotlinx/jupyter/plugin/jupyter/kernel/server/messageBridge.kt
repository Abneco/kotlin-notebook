// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.jsonObject
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process.socketType
import org.jetbrains.kotlinx.jupyter.plugin.util.toJacksonJson
import org.jetbrains.kotlinx.jupyter.plugin.util.toKotlinSerializationJson
import org.jetbrains.kotlinx.jupyter.protocol.RawMessageImpl
import com.intellij.jupyter.core.jackson
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageBase
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessageChannel
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterProtocolSchemaFactory

private val messageBytePrefix = listOf(byteArrayOf(1))

data class RawMessageWithSocket(
    val rawMessage: RawMessage,
    val socketType: JupyterSocketType,
)

internal fun <T> JupyterMessage.asRawMessage(
    action: (rawMessage: RawMessage, socketType: JupyterSocketType) -> T
): T? {
    val socketType = channel.socketType ?: return null
    val rawMessage = RawMessageImpl(
        messageBytePrefix,
        header.json.toKotlinSerializationJson().jsonObject,
        parentHeader?.json?.toKotlinSerializationJson()?.jsonObject,
        null,
        messageContent.toKotlinSerializationJson(),
    )

    return action(rawMessage, socketType)
}

internal fun JupyterMessage.toRawMessageWithSocket(): RawMessageWithSocket? {
    return asRawMessage(::RawMessageWithSocket)
}

internal fun RawMessage.toJupyterMessage(channel: JupyterMessageChannel): JupyterMessage {
    val schema = JupyterProtocolSchemaFactory.createSchema()
    val json = jackson.createObjectNode().apply {
        put(schema.channelFieldsName, channel.value)
        set<JsonNode>(schema.headerFieldName, header.toJacksonJson())
        set<JsonNode>(schema.parentHeaderFieldName, parentHeader?.toJacksonJson())
        set<JsonNode>("metadata", metadata?.toJacksonJson())
        set<JsonNode>(schema.contentFieldName, content.toJacksonJson())
    }
    return JupyterMessageBase(
        json,
        id,
        schema
    )
}
