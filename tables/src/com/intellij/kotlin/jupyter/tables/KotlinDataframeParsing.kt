// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.tables

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.database.datagrid.DynamicNestedTable
import com.intellij.database.datagrid.StaticNestedTable
import com.intellij.database.extractors.ImageInfo
import com.intellij.jupyter.core.jupyter.nbformat.MimeType
import com.intellij.scientific.tables.nestedTable.ColumnTreeNode
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.GZIPInputStream

private const val SERIALIZED_DATAFRAME_FIELD = "kotlin_dataframe"
private const val COLUMNS_FIELD = "columns"
private const val TYPES_FIELD = "types"
private const val NUM_ROWS_FIELD = "nrow"
private const val NUM_COLS_FIELD = "ncol"
private const val VERSION_FIELD = $$"$version"
private const val DATA_FIELD = "data"
private const val METADATA_FIELD = "metadata"
private const val COLUMN_KIND_FIELD = "kind"
internal const val VALUE_COLUMN = "ValueColumn"
internal const val COLUMN_GROUP = "ColumnGroup"
internal const val FRAME_COLUMN = "FrameColumn"
internal const val FRAME_CONVERTABLE = "DataFrameConvertable"
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

    private val mapper by lazy { ObjectMapper() }

    /** Returns `true` if [dataObject] represents a dataframe that has been formatted by the user. */
    fun dataFrameIsFormatted(dataObject: ObjectNode): Boolean {
        val jsonPayload = dataObject[MimeType.KOTLIN_DATAFRAME.mimeType]?.asText() ?: return false
        val jsonObject = mapper.readTree(jsonPayload)
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
        } else {
            KotlinDataframeParserFormatV1(mapper)
        }
    }
}

interface KotlinDataframeParser {
    fun parseDataFrameInfo(serializedData: String): KotlinDataframeInfo

    fun parseDataFrameData(serializedData: String): List<ColumnValues>
}

data class KotlinDataframeInfo(
    val rowsNum: Int,
    val totalRowsNum: Int,
    val topLevelColumnNames: List<String>,
    val topLevelTypeNames: List<String>,
    val columnTreeRoot: ColumnTreeNode
)

typealias ColumnValues = List<Any>

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
    private val mapper: ObjectMapper,
    private val pathToData: List<String>,
    private val pathToMetadata: List<String>,
    private val isNestedFrameStatic: Boolean,
    private val extractColumnData: (JsonNode) -> JsonNode = { it },
    private val extractNestedTablesRowNum: (JsonNode) -> Int = { (it as ArrayNode).size() },
    private val base64Pattern: Regex = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")
) : KotlinDataframeParser {
    override fun parseDataFrameInfo(serializedData: String): KotlinDataframeInfo {
        val rawJson = mapper.extractRawJson(serializedData)

        val rows = rawJson.getByPath(pathToData) as ArrayNode
        val metadata = rawJson.getByPath(pathToMetadata) as ObjectNode
        val columnTypes: List<String> = metadata[TYPES_FIELD]?.map { it: JsonNode ->
            if (it is ObjectNode) {
                when (it[COLUMN_KIND_FIELD].asText()) {
                    VALUE_COLUMN -> it["type"].asText()
                    COLUMN_GROUP -> COLUMN_GROUP
                    FRAME_COLUMN -> FRAME_COLUMN
                    else -> ""
                }
            } else {
                ""
            }
        } ?: List(metadata[COLUMNS_FIELD].size()) { "" }

        if (rows.isEmpty || rows.all { it == null || it.isNull }) {
            val columnNames = metadata[COLUMNS_FIELD].map { it.asText() }.ifEmpty { listOf(" ") }
            return KotlinDataframeInfo(
                0,
                0,
                columnNames,
                columnTypes.ifEmpty { listOf(" ") },
                createRoot(columnNames)
            )
        }

        val root = rows.first().extractColumnsHierarchy()
        val columnNames = root.columnChildren.map { it.columnName }
        return KotlinDataframeInfo(
            rows.size(),
            metadata[NUM_ROWS_FIELD].asInt(),
            columnNames,
            columnTypes,
            root
        )
    }

    override fun parseDataFrameData(serializedData: String): List<ColumnValues> {
        val rawJson = mapper.extractRawJson(serializedData)

        val rows = try {
            rawJson.getByPath(pathToData) as ArrayNode
        } catch (t: Throwable) {
            throw IllegalArgumentException("Invalid dataframe data format. Expected an array of rows. Text:\n$serializedData", t)
        }
        if (rows.isEmpty) return emptyList()

        val root = rows.first().extractColumnsHierarchy()

        val columnValues = List(root.columnChildren.size) { mutableListOf<Any>() }

        for (row in rows) {
            val values = row.extractRowValues(root.columnChildren, isNestedFrameStatic)
            values.forEachIndexed { index, any -> columnValues[index].add(any) }
        }

        return columnValues
    }

    private fun JsonNode.extractColumnsHierarchy(): ColumnTreeNode {
        val root = createRoot()

        var index = 0
        fun extractColumnsHelper(jsonNode: JsonNode, columnsNode: ColumnTreeNode, path: List<String>) {
            var childIdx = 0
            val columnData = extractColumnData(jsonNode)
            if (columnData.isObject) {
                columnData.properties()?.forEach { (key, value) ->
                    val child = ColumnTreeNode(key, index++, childIdx++, mutableListOf())
                    columnsNode.columnChildren.add(child)
                    extractColumnsHelper(value, child, path + listOf(key))
                }
            }
        }

        extractColumnsHelper(this, root, emptyList())

        return root
    }

    private fun JsonNode.extractRowValues(columns: List<ColumnTreeNode>, isNestedFrameStatic: Boolean): List<Any> {
        return columns.map { column ->
            when {
                isValueNode && (column.name == "value" || column.name == "array") -> handleAutogeneratedColumn(column)
                isObject && has(column.name) -> {
                    val columnJson = get(column.name)
                    val data = extractColumnData(columnJson)
                    val columnValue = data.extractColumnValue(column, columnJson.isFrameLike() || isNestedFrameStatic)
                    if (columnValue is DynamicNestedTable) columnValue.totalRowsNum = extractNestedTablesRowNum(columnJson)
                    columnValue
                }
                // It is normal to return null here.
                // This situation occurs when there is a mixture of primitives and objects in a nested dataframe.
                // In this case autogenerated columns are created for, but other columns do not have any values.
                else -> NULL
            }
        }
    }

    private fun JsonNode.handleAutogeneratedColumn(column: ColumnTreeNode): Any {
        return if (isArray) deserializeMultidimensionalArrayOfPrimitives() else extractColumnValue(column, isNestedFrameStatic)
    }

    private fun JsonNode.extractColumnValue(column: ColumnTreeNode, isNestedFrameStatic: Boolean): Any {
        return when {
            isValueNode -> deserializePrimitive()
            isObject -> extractRowValues(column.columnChildren, isNestedFrameStatic)
            isArray -> extractArrayValue(isNestedFrameStatic)
            else -> throw IllegalArgumentException("Unsupported JsonNode type encountered when trying to extract value for column: ${column.name} from node: ${this}")
        }
    }

    private fun JsonNode.extractArrayValue(isNestedFrameStatic: Boolean): Iterable<*> {
        return if (isMultidimensionalArrayOfPrimitives()) {
            deserializeMultidimensionalArrayOfPrimitives()
        } else {
            // Nested Dataframe case
            val nestedTableHierarchy = extractNestedTableHierarchy()
            if (isNestedFrameStatic) {
                val nestedRows: Array<Array<Any>> = map { arrayNode ->
                    arrayNode.extractRowValues(nestedTableHierarchy.columnChildren, true).toTypedArray()
                }.toTypedArray()
                StaticNestedTable(nestedRows, nestedTableHierarchy)
            } else {
                val nestedRows: List<Array<Any>> = map { arrayNode ->
                    arrayNode.extractRowValues(nestedTableHierarchy.columnChildren, false).toTypedArray()
                }.toList()
                DynamicNestedTable(nestedRows, nestedTableHierarchy)
            }
        }
    }

    /**
     *  Determines whether the given [JsonNode] represents a multidimensional array of primitives.
     *  It is safe to check only on the top level because of the way Dataframe serialization works.
     *	It is impossible for objects to be mixed with primitives on any level except the top one.
     */
    private fun JsonNode.isMultidimensionalArrayOfPrimitives(): Boolean = all { !it.isObject }

    private fun JsonNode.deserializePrimitive(): Any {
        return when {
            isTextual -> {
                val text = asText()
                processTextPrimitive(text)
            }
            isBoolean -> asBoolean()
            isNumber -> asNumber()
            isBinary -> binaryValue()
            isNull -> NULL
            else -> asText()
        }
    }

    private fun processTextPrimitive(text: String): Any {
        val bytes = tryDecodeBase64(text)?.let { bytes ->
            if (isGzipCompressed(bytes)) {
                decompressGzip(bytes)
            } else {
                bytes
            }
        }

        return if (bytes != null) {
            val info = ImageInfo.tryDetectImage(bytes)
            info ?: text
        } else {
            text
        }
    }

    private fun tryDecodeBase64(text: String): ByteArray? {
        if (!base64Pattern.matches(text)) {
            return null
        }

        return try {
            Base64.getDecoder().decode(text)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun isGzipCompressed(bytes: ByteArray): Boolean {
        return if (bytes.size < 3) {
            false
        } else {
            val gzipSignature = bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte() && bytes[2] == 0x08.toByte()
            val freezeSignature = bytes[0] == 0x1F.toByte() && bytes[1] == 0x9E.toByte()
            gzipSignature || freezeSignature
        }
    }

    fun decompressGzip(input: ByteArray): ByteArray {
        return ByteArrayOutputStream().use { byteArrayOutputStream ->
            GZIPInputStream(input.inputStream()).use { inputStream ->
                inputStream.copyTo(byteArrayOutputStream)
            }
            byteArrayOutputStream.toByteArray()
        }
    }

    private fun JsonNode.asNumber() = if (isIntegralNumber) numberValue() else asDouble()

    private fun JsonNode.deserializeMultidimensionalArrayOfPrimitives(): List<*> {
        return map { if (it.isArray) it.deserializeMultidimensionalArrayOfPrimitives() else it.deserializePrimitive() }
    }

    private fun JsonNode.extractNestedTableHierarchy(): ColumnTreeNode {
        return find { it.isObject }?.extractColumnsHierarchy() ?: createRoot()
    }
}

private fun JsonNode.isFrame(): Boolean {
    return columnKind() == FRAME_COLUMN
}

private fun JsonNode.isFrameLike(): Boolean {
    return columnKind() == FRAME_CONVERTABLE
}

private fun JsonNode.isColumnGroup(): Boolean {
    return columnKind() == COLUMN_GROUP
}

private val pathToColumnKind = listOf(METADATA_FIELD, COLUMN_KIND_FIELD)

private fun JsonNode.columnKind(): String? {
    return getByPath(pathToColumnKind)?.asText()
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

private fun JsonNode.getByPath(path: List<String>): JsonNode? {
    var result: JsonNode = this
    for (field in path) {
        result = result.get(field) ?: return null
    }

    return result
}

private fun createRoot() = ColumnTreeNode("root", -1, 0, mutableListOf())

internal fun createRoot(childrenNames: List<String>): ColumnTreeNode {
    val root = createRoot()
    for ((index, column) in childrenNames.withIndex()) {
        root.columnChildren.add(ColumnTreeNode(column, index, index, mutableListOf()))
    }

    return root
}