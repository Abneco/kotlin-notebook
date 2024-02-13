// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.database.datagrid.DynamicNestedTable
import org.jetbrains.plugins.notebooks.tables.ColumnTreeNode


const val KOTLIN_DATAFRAME_MIME: String = "application/kotlindataframe+json"
private const val JSON_PAYLOAD_FIELD = KOTLIN_DATAFRAME_MIME
private const val SERIALIZED_DATAFRAME_FIELD = "kotlin_dataframe"
private const val COLUMNS_FIELD = "columns"
private const val NUM_ROWS_FIELD = "nrow"
private const val NUM_COLS_FIELD = "ncol"
private const val VERSION_FIELD = "\$version"
private const val DATA_FIELD = "data"
private const val METADATA_FIELD = "metadata"
private const val COLUMN_KIND_FIELD = "kind"
private const val COLUMN_GROUP = "ColumnGroup"
private const val FRAME_COLUMN = "FrameColumn"

/**
 * Utils for parsing encoded table data produced by Kotlin Dataframe library
 */
object KotlinDataframeParsing {

    fun isKotlinDataFrame(dataObject: ObjectNode): Boolean {
        if (!dataObject.has(JSON_PAYLOAD_FIELD)) return false
        val jsonPayload = dataObject[JSON_PAYLOAD_FIELD].asText() ?: return false

        return jsonPayload.contains(SERIALIZED_DATAFRAME_FIELD)
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
    val rowsNum: Int, val totalRowsNum: Int, val topLevelColumnNames: List<String>,
    val columnTreeRoot: ColumnTreeNode
)

typealias ColumnValues = List<Any>

class KotlinDataframeParserFormatV1(mapper: ObjectMapper) :
    KotlinDataframeParser by KotlinDataframeParserImpl(
        mapper,
        pathToData = listOf(SERIALIZED_DATAFRAME_FIELD),
        pathToMetadata = emptyList()
    )

class KotlinDataframeParserFormatV2(mapper: ObjectMapper) :
    KotlinDataframeParser by KotlinDataframeParserImpl(
        mapper,
        pathToData = listOf(SERIALIZED_DATAFRAME_FIELD),
        pathToMetadata = listOf(METADATA_FIELD),
        extractColumnData = { node -> if (node.isColumnGroup() || node.isFrame()) node[DATA_FIELD] else node },
        extractNestedTablesRowNum = { node -> node[METADATA_FIELD][NUM_ROWS_FIELD].asInt() }
    )

private class KotlinDataframeParserImpl(
    private val mapper: ObjectMapper,
    private val pathToData: List<String>,
    private val pathToMetadata: List<String>,
    private val extractColumnData: (JsonNode) -> JsonNode = { it },
    private val extractNestedTablesRowNum: (JsonNode) -> Int = { (it as ArrayNode).size() }
) : KotlinDataframeParser {
    override fun parseDataFrameInfo(serializedData: String): KotlinDataframeInfo {
        val rawJson = mapper.extractRawJson(serializedData)

        val rows = rawJson.getByPath(pathToData) as ArrayNode
        val metadata = rawJson.getByPath(pathToMetadata)

        if (rows.isEmpty) {
            val columnNames = metadata[COLUMNS_FIELD].map { it.asText() }.ifEmpty { listOf(" ") }
            return KotlinDataframeInfo(
                0,
                0,
                columnNames,
                createRoot(columnNames)
            )
        }

        val root = rows.first().extractColumnsHierarchy()
        val columnNames = root.columnChildren.map { it.columnName }

        return KotlinDataframeInfo(
            rows.size(),
            metadata[NUM_ROWS_FIELD].asInt(),
            columnNames,
            root
        )
    }

    override fun parseDataFrameData(serializedData: String): List<List<Any>> {
        val rawJson = mapper.extractRawJson(serializedData)

        val rows = rawJson.getByPath(pathToData) as ArrayNode
        if (rows.isEmpty) return emptyList()

        val root = rows.first().extractColumnsHierarchy()

        val columnValues = List(root.columnChildren.size) { mutableListOf<Any>() }

        for (row in rows) {
            val values = row.extractRowValues(root.columnChildren)
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
                columnData.fields()?.forEach { (key, value) ->
                    val child = ColumnTreeNode(key, index++, childIdx++, mutableListOf())
                    columnsNode.columnChildren.add(child)
                    extractColumnsHelper(value, child, path + listOf(key))
                }
            }
        }

        extractColumnsHelper(this, root, emptyList())

        return root
    }

    private fun JsonNode.extractRowValues(columns: List<ColumnTreeNode>): List<Any> {
        return columns.map { column ->
            when {
                isValueNode && (column.name == "value" || column.name == "array") -> handleAutogeneratedColumn(column)
                isObject && has(column.name) -> {
                    val columnJson = get(column.name)
                    val data = extractColumnData(columnJson)
                    val columnValue = data.extractColumnValue(column)
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
        return if (isArray) deserializeMultidimensionalArrayOfPrimitives() else extractColumnValue(column)
    }

    private fun JsonNode.extractColumnValue(column: ColumnTreeNode): Any {
        return when {
            isValueNode -> deserializePrimitive()
            isObject -> extractRowValues(column.columnChildren)
            isArray -> extractArrayValue()
            else -> throw IllegalArgumentException("Unsupported JsonNode type encountered when trying to extract value for column: ${column.name} from node: ${this}")
        }
    }

    private fun JsonNode.extractArrayValue(): Iterable<*> {
        return if (isMultidimensionalArrayOfPrimitives()) {
            deserializeMultidimensionalArrayOfPrimitives()
        } else {
            // Nested Dataframe case
            val nestedTableHierarchy = extractNestedTableHierarchy()
            val nestedRows: List<Array<Any>> = map { arrayNode ->
                arrayNode.extractRowValues(nestedTableHierarchy.columnChildren).toTypedArray()
            }.toList()
            DynamicNestedTable(nestedRows, nestedTableHierarchy)
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
            isTextual -> asText()
            isBoolean -> asBoolean()
            isNumber -> asNumber()
            isBinary -> binaryValue()
            isNull -> NULL
            else -> asText()
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
    return has(METADATA_FIELD) && this[METADATA_FIELD][COLUMN_KIND_FIELD].asText() == FRAME_COLUMN
}

private fun JsonNode.isColumnGroup(): Boolean {
    return has(METADATA_FIELD) && this[METADATA_FIELD][COLUMN_KIND_FIELD].asText() == COLUMN_GROUP
}

private fun ObjectMapper.extractRawJson(text: String): JsonNode {
    val data = readTree(text)
    return readTree(data[JSON_PAYLOAD_FIELD].asText())
}

private fun JsonNode.getByPath(path: List<String>): JsonNode {
    var result: JsonNode = this
    for (field in path) {
        result = result.get(field)
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
