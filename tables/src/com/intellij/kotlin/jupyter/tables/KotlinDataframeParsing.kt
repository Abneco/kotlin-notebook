// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.tables

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.database.extractors.ImageInfo
import com.intellij.jupyter.core.jackson
import com.intellij.jupyter.core.jupyter.nbformat.MimeType
import com.intellij.notebooks.dataframe.COLUMNS_FIELD
import com.intellij.notebooks.dataframe.DATA_FIELD
import com.intellij.notebooks.dataframe.KotlinDataframeParser
import com.intellij.notebooks.dataframe.KotlinDataframeParserBase
import com.intellij.notebooks.dataframe.METADATA_FIELD
import com.intellij.notebooks.dataframe.NUM_COLS_FIELD
import com.intellij.notebooks.dataframe.NUM_ROWS_FIELD
import com.intellij.notebooks.dataframe.SERIALIZED_DATAFRAME_FIELD
import com.intellij.notebooks.dataframe.VERSION_FIELD
import com.intellij.notebooks.dataframe.isColumnGroup
import com.intellij.notebooks.dataframe.isFrame
import com.intellij.notebooks.dataframe.isFrameLike
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.GZIPInputStream

internal const val IS_FORMATTED = "is_formatted"

/**
 * Utils for parsing encoded table data produced by the Kotlin Dataframe library
 */
object KotlinDataframeParsing {

    fun isKotlinDataFrame(dataObject: ObjectNode): Boolean {
        if (!dataObject.has(MimeType.KOTLIN_DATAFRAME.mimeType)) return false
        val jsonPayload = dataObject[MimeType.KOTLIN_DATAFRAME.mimeType].asText() ?: return false

        return jsonPayload.contains(SERIALIZED_DATAFRAME_FIELD)
    }

    /** Returns `true` if [dataObject] represents a dataframe that has been formatted by the user. */
    fun isFormattedDataFrame(dataObject: ObjectNode): Boolean {
        val jsonPayload = dataObject[MimeType.KOTLIN_DATAFRAME.mimeType]?.asText() ?: return false
        val jsonObject = jackson.readTree(jsonPayload)
        val metadata = jsonObject[METADATA_FIELD] as ObjectNode? ?: return false

        return metadata[IS_FORMATTED] == BooleanNode.TRUE
    }

    fun isFormatSupported(serializedData: String): Boolean {
        return serializedData.contains(SERIALIZED_DATAFRAME_FIELD) &&
               serializedData.contains(NUM_COLS_FIELD) &&
               serializedData.contains(NUM_ROWS_FIELD) &&
               serializedData.contains(COLUMNS_FIELD)
    }

    fun createParserForData(serializedData: String, mapper: ObjectMapper): KotlinDataframeParser {
        return if (serializedData.contains(VERSION_FIELD)) {
            KotlinDataframeParserFormatV2(mapper)
        }
        else {
            KotlinDataframeParserFormatV1(mapper)
        }
    }
}

class KotlinDataframeParserFormatV1(mapper: ObjectMapper) :
    KotlinDataframeParser by KotlinDataframeParserImpl(
        mapper,
        pathToData = listOf(SERIALIZED_DATAFRAME_FIELD),
        pathToMetadata = emptyList(),
        isNestedFrameStatic = true
    )

class KotlinDataframeParserFormatV2(mapper: ObjectMapper) :
    KotlinDataframeParser by KotlinDataframeParserImpl(
        mapper,
        pathToData = listOf(SERIALIZED_DATAFRAME_FIELD),
        pathToMetadata = listOf(METADATA_FIELD),
        isNestedFrameStatic = false,
        extractColumnData = { node -> if (node.isColumnGroup() || node.isFrame() || node.isFrameLike()) node[DATA_FIELD] else node },
        extractNestedTablesRowNum = { node -> node[METADATA_FIELD][NUM_ROWS_FIELD].asInt() }
    )

private class KotlinDataframeParserImpl(
    mapper: ObjectMapper,
    pathToData: List<String>,
    pathToMetadata: List<String>,
    isNestedFrameStatic: Boolean,
    extractColumnData: (JsonNode) -> JsonNode = { it },
    extractNestedTablesRowNum: (JsonNode) -> Int = { (it as ArrayNode).size() },
) : KotlinDataframeParserBase(
    mapper,
    pathToData,
    pathToMetadata,
    isNestedFrameStatic,
    extractColumnData,
    extractNestedTablesRowNum
) {
    override fun getRawJson(serializedData: String): JsonNode = mapper.extractRawJson(serializedData)

    override fun processTextPrimitive(text: String): Any {
        val bytes = tryDecodeBase64(text)?.let { bytes ->
            if (isGzipCompressed(bytes)) {
                decompressGzip(bytes)
            }
            else {
                bytes
            }
        }

        return if (bytes != null) {
            val info = ImageInfo.tryDetectImage(bytes)
            info ?: text
        }
        else {
            text
        }
    }

    private val base64Pattern: Regex = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")

    private fun tryDecodeBase64(text: String): ByteArray? {
        if (!base64Pattern.matches(text)) {
            return null
        }

        return try {
            Base64.getDecoder().decode(text)
        }
        catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun isGzipCompressed(bytes: ByteArray): Boolean {
        return if (bytes.size < 3) {
            false
        }
        else {
            val gzipSignature = bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte() && bytes[2] == 0x08.toByte()
            val freezeSignature = bytes[0] == 0x1F.toByte() && bytes[1] == 0x9E.toByte()
            gzipSignature || freezeSignature
        }
    }

    private fun decompressGzip(input: ByteArray): ByteArray {
        return ByteArrayOutputStream().use { byteArrayOutputStream ->
            GZIPInputStream(input.inputStream()).use { inputStream ->
                inputStream.copyTo(byteArrayOutputStream)
            }
            byteArrayOutputStream.toByteArray()
        }
    }
}

private fun ObjectMapper.extractRawJson(text: String): JsonNode {
    val data = readTree(text)
    //Code wrote is really bad by architecture and in the same time there is expected jupyter output with mimeType and it is possible correct output
    //It should be rewritten
    if (data.has(MimeType.KOTLIN_DATAFRAME.mimeType)) {
        val rawData = data[MimeType.KOTLIN_DATAFRAME.mimeType].asText()
        return readTree(rawData)
    }
    return data
}