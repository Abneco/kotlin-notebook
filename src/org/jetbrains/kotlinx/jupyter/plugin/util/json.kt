package org.jetbrains.kotlinx.jupyter.plugin.util

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

private fun convertObject(objectNode: ObjectNode): JsonObject {
    return buildJsonObject {
        objectNode.fields().forEachRemaining {
            put(it.key, it.value.toKotlinSerializationJson())
        }
    }
}

inline fun <reified T> JsonNode.deserialize(): T {
    val json = toKotlinSerializationJson()
    return Json.decodeFromJsonElement(json)
}

class JacksonJsonConversionException(node: JsonNode) :
    IllegalArgumentException("Node of type ${node::class} cannot be deserialized: $node")
