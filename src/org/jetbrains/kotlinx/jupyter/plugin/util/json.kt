package org.jetbrains.kotlinx.jupyter.plugin.util

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BinaryNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.POJONode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.databind.node.ValueNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import com.intellij.jupyter.core.jackson

val jsonConfig = Json {
    ignoreUnknownKeys = true
}

fun JsonNode.toKotlinSerializationJson(): JsonElement {
    return when (this) {
        is ValueNode -> convertPrimitive(this)
        is ArrayNode -> convertArray(this)
        is ObjectNode -> convertObject(this)
        else -> throw JacksonJsonConversionException(this)
    }
}

private fun convertPrimitive(value: ValueNode): JsonPrimitive {
    return when (value) {
        is NullNode, is MissingNode -> JsonNull
        is NumericNode -> JsonPrimitive(value.numberValue())
        is BooleanNode -> JsonPrimitive(value.booleanValue())
        is TextNode, is BinaryNode, is POJONode -> JsonPrimitive(value.textValue())
        else -> throw JacksonJsonConversionException(value)
    }
}

private fun convertArray(array: ArrayNode): JsonArray {
    return buildJsonArray {
        array.elements().forEachRemaining {
            add(it.toKotlinSerializationJson())
        }
    }
}

fun convertObject(objectNode: ObjectNode): JsonObject {
    return buildJsonObject {
        objectNode.fields().forEachRemaining {
            put(it.key, it.value.toKotlinSerializationJson())
        }
    }
}

inline fun <reified T> JsonNode.deserialize(): T? {
    val json = toKotlinSerializationJson()
    if (json is JsonNull) return null
    return jsonConfig.decodeFromJsonElement(json)
}

class JacksonJsonConversionException(node: JsonNode) :
    IllegalArgumentException("Node of type ${node::class} cannot be deserialized: $node")

class KotlinxSerializationConversionException(node: JsonElement, cause: Throwable? = null) :
    IllegalArgumentException("Node $node cannot be converted to Jackson JSON", cause)

fun JsonElement.toJacksonJson(): JsonNode {
    return when(this) {
        JsonNull -> NullNode.instance
        is JsonPrimitive -> convertPrimitive(this)
        is JsonObject -> convertObject(this)
        is JsonArray -> convertArray(this)
    }
}

private fun convertPrimitive(value: JsonPrimitive): ValueNode {
    return if (value.isString) {
        TextNode(value.content)
    } else {
        try {
            jackson.readValue(value.content, ValueNode::class.java)
        } catch (e: JsonProcessingException) {
            throw KotlinxSerializationConversionException(value, e)
        }
    }
}

private fun convertArray(array: JsonArray): ArrayNode {
    return jackson.createArrayNode().apply {
        for (element in array) {
            add(element.toJacksonJson())
        }
    }
}

private fun convertObject(objectNode: JsonObject): ObjectNode {
    return jackson.createObjectNode().apply {
        for (entry in objectNode) {
            set<JsonNode>(entry.key, entry.value.toJacksonJson())
        }
    }
}
