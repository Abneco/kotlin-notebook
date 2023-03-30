package org.jetbrains.kotlinx.jupyter.plugin.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BinaryNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.FloatNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.LongNode
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.jetbrains.plugins.notebooks.jackson

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

internal fun convertObject(objectNode: ObjectNode): JsonObject {
    return buildJsonObject {
        objectNode.fields().forEachRemaining {
            put(it.key, it.value.toKotlinSerializationJson())
        }
    }
}

inline fun <reified T> JsonNode.deserialize(): T? {
    val json = toKotlinSerializationJson()
    if (json is JsonNull) return null
    return Json.decodeFromJsonElement(json)
}

class JacksonJsonConversionException(node: JsonNode) :
    IllegalArgumentException("Node of type ${node::class} cannot be deserialized: $node")

class KotlinxSerializationConversionException(node: JsonElement) :
        IllegalArgumentException("Node $node cannot be converted to Jackson JSON")

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
        with(value) {
            intOrNull?.let { IntNode(it) } ?:
            longOrNull?.let { LongNode(it) } ?:
            floatOrNull?.let { FloatNode(it) } ?:
            doubleOrNull?.let { DoubleNode(it) } ?:
            booleanOrNull?.let { BooleanNode.valueOf(it) } ?:
            throw KotlinxSerializationConversionException(value)
        }

    }
}

private fun convertArray(array: JsonArray): ArrayNode {
    return jackson.createArrayNode().apply {
        array.forEach { element ->
            add(element.toJacksonJson())
        }
    }
}

private fun convertObject(objectNode: JsonObject): ObjectNode {
    return jackson.createObjectNode().apply {
        objectNode.forEach { (key, node) ->
            set<JsonNode>(key, node.toJacksonJson())
        }
    }
}
