// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.notekit

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.jupyter.core.jackson

/**
 * Constants for the Notekit Protocol v1.
 * See PROTOCOL.md for the full specification.
 */
object NotekitProtocol {
    const val TARGET_NAME: String = "jupyter.notekit.v1"

    // Error codes
    const val ERROR_INVALID_RANGE: String = "INVALID_RANGE"
    const val ERROR_OUT_OF_BOUNDS: String = "OUT_OF_BOUNDS"
    const val ERROR_INVALID_SPLICE_PARAMS: String = "INVALID_SPLICE_PARAMS"
    const val ERROR_INVALID_METADATA: String = "INVALID_METADATA"
    const val ERROR_INVALID_CELL_DATA: String = "INVALID_CELL_DATA"
    const val ERROR_EXECUTION_FAILED: String = "EXECUTION_FAILED"
    const val ERROR_UNKNOWN_METHOD: String = "UNKNOWN_METHOD"
    const val ERROR_INTERNAL_ERROR: String = "INTERNAL_ERROR"
    const val ERROR_NO_ACTIVE_NOTEBOOK: String = "NO_ACTIVE_NOTEBOOK"
}

/**
 * Parsed request from kernel.
 * Parameters are passed at the top level of the request (not in a nested "params" object).
 */
class NotekitRequest private constructor(
    val method: String,
    val requestId: String,
    private val data: JsonNode,
) {
    /** Get optional int parameter, returns null if missing */
    fun intParam(name: String): Int? = data[name]?.asInt()

    /** Get required int parameter, returns default if missing */
    fun intParam(name: String, default: Int): Int = data[name]?.asInt() ?: default

    /** Get optional boolean parameter */
    fun boolParam(name: String, default: Boolean): Boolean = data[name]?.asBoolean() ?: default

    /** Get optional JSON node parameter */
    fun nodeParam(name: String): JsonNode? = data[name]

    /** Get params as ArrayNode if it contains "cells" array */
    fun cellsParam(): ArrayNode? = data["cells"] as? ArrayNode

    /** Create success response for this request */
    fun success(result: ObjectNode = jackson.createObjectNode()): ObjectNode =
        NotekitResponse.success(requestId, result)

    /** Create error response for this request */
    fun error(code: String, message: String): ObjectNode =
        NotekitResponse.error(requestId, code, message)

    companion object {
        fun parse(data: JsonNode): NotekitRequest? {
            val method = data["method"]?.asText() ?: return null
            val requestId = data["request_id"]?.asText() ?: return null
            return NotekitRequest(method, requestId, data)
        }
    }
}

/**
 * Builder for response messages.
 */
private object NotekitResponse {
    fun success(requestId: String, result: ObjectNode): ObjectNode = jackson.createObjectNode().apply {
        put("request_id", requestId)
        put("status", "ok")
        set<ObjectNode>("result", result)
    }

    fun error(requestId: String, code: String, message: String): ObjectNode = jackson.createObjectNode().apply {
        put("request_id", requestId)
        put("status", "error")
        set<ObjectNode>("error", jackson.createObjectNode().apply {
            put("code", code)
            put("message", message)
        })
    }
}

/**
 * Cell data for protocol communication.
 */
data class CellData(
    val cellType: String,
    val source: String,
    val metadata: JsonNode = jackson.createObjectNode(),
) {
    companion object {
        fun fromJson(node: JsonNode): CellData? {
            val cellType = node["cell_type"]?.asText() ?: return null
            val source = when (val sourceNode = node["source"]) {
                is ArrayNode -> sourceNode.joinToString("") { it.asText() }
                else -> sourceNode?.asText() ?: ""
            }
            return CellData(
                cellType = cellType,
                source = source,
                metadata = node["metadata"] ?: jackson.createObjectNode(),
            )
        }
    }
}
