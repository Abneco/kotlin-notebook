// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.database.datagrid.ArrayBackedNestedTable
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.COLUMN_GROUP
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.FRAME_COLUMN
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.COLUMN_KIND_FIELD
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.DATA_FIELD
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.METADATA_FIELD
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.COLUMNS_FIELD
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.NUM_ROWS_FIELD
import org.jetbrains.plugins.notebooks.tables.ColumnTreeNode

/**
 * Utils for parsing encoded table data produced by Kotlin Dataframe library
 */
object KotlinDataframeParsing {
    const val JSON_PAYLOAD_FIELD = "application/kotlindataframe+json"
    const val SERIALIZED_DATAFRAME_FIELD = "kotlin_dataframe"
    const val COLUMNS_FIELD = "columns"
    const val NUM_ROWS_FIELD = "nrow"
    const val NUM_COLS_FIELD = "ncol"
    const val VERSION_FIELD = "\$version"
    const val DATA_FIELD = "data"
    const val METADATA_FIELD = "metadata"
    const val COLUMN_KIND_FIELD = "kind"
    const val COLUMN_GROUP = "ColumnGroup"
    const val FRAME_COLUMN = "FrameColumn"

    fun isKotlinDataFrame(dataObject: ObjectNode): Boolean {
        if (!dataObject.has(JSON_PAYLOAD_FIELD)) return false
        val jsonPayload = dataObject[JSON_PAYLOAD_FIELD].asText() ?: return false

        return jsonPayload.contains(SERIALIZED_DATAFRAME_FIELD)
    }
}

data class KotlinDataframeInfo(
    val rowsNum: Int, val totalRowsNum: Int, val topLevelColumnNames: List<String>,
    val columnTreeRoot: ColumnTreeNode
)

interface KotlinDataframeParser<FrameInfo, FrameData> {
    fun parseDataFrameInfo(serializedData: String): FrameInfo

    fun parseDataFrameData(serializedData: String): FrameData
}

class KotlinDataframeParserImpl(private val pathToData: List<String>, private val pathToMetadata: List<String>,
                                private val isFormatV2: Boolean, private val mapper: ObjectMapper) :
    KotlinDataframeParser<KotlinDataframeInfo, List<List<Any>>> {
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

        val root = rows.first().extractHierarchy(isFormatV2)
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

        val root = rows.first().extractHierarchy(isFormatV2)

        val columnValues = List(root.columnChildren.size) { mutableListOf<Any>() }

        for (row in rows) {
            val values = row.extractValues(root.columnChildren, isFormatV2)
            values.forEachIndexed { index, any -> columnValues[index].add(any) }
        }

        return columnValues
    }
}

private fun ObjectMapper.extractRawJson(text: String): JsonNode {
    val data = readTree(text)
    return readTree(data[KotlinDataframeParsing.JSON_PAYLOAD_FIELD].asText())
}

private fun JsonNode.extractHierarchy(isFormatV2: Boolean): ColumnTreeNode {
    val root = createRoot()

    var index = 0
    fun extractColumnsHelper(jsonNode: JsonNode, columnsNode: ColumnTreeNode, path: List<String>) {
        var childIdx = 0
        if (jsonNode.isObject) {
            if (isFormatV2 && jsonNode.isFrame()) return

            val columns = if (jsonNode.isColumnGroup()) jsonNode.get(DATA_FIELD) else jsonNode
            columns.fields().forEach { (key, value) ->
                val child = ColumnTreeNode(key, index++, childIdx++, mutableListOf())
                columnsNode.columnChildren.add(child)
                extractColumnsHelper(value, child, path + listOf(key))
            }
        }
    }

    extractColumnsHelper(this, root, emptyList())

    return root
}

private fun JsonNode.extractValues(columns: List<ColumnTreeNode>, isFormatV2: Boolean): List<Any> {
    return columns.map { column ->
        when {
            isValueNode && (column.name == "value" || column.name == "array") -> handleAutogeneratedColumn(column, isFormatV2)
            isObject && has(column.name) -> {
                val value = get(column.name)
                if (value.isColumnGroup() || value.isFrame()) {
                    value[DATA_FIELD].extractValue(column, isFormatV2)
                } else {
                    value.extractValue(column, isFormatV2)
                }
            }
            // It is normal to return null here.
            // This situation occurs when there is a mixture of primitives and objects in a nested dataframe.
            // In this case autogenerated columns are created for, but other columns do not have any values.
            else -> NULL
        }
    }
}

private fun JsonNode.handleAutogeneratedColumn(column: ColumnTreeNode, isFormatV2: Boolean): Any {
    return if (isArray) deserializeArray() else extractValue(column, isFormatV2)
}

private fun JsonNode.extractValue(column: ColumnTreeNode, isFormatV2: Boolean): Any {
    return when {
        isValueNode -> deserializeValue()
        isObject -> extractValues(column.columnChildren, isFormatV2)
        isArray -> extractArrayValue(isFormatV2)
        else -> throw IllegalArgumentException("Unsupported JsonNode type encountered when trying to extract value for column: ${column.name} from node: ${this}")
    }
}

private fun JsonNode.extractArrayValue(isFormatV2: Boolean): Iterable<*> {
    return if (isMultidimensionalArrayOfPrimitives()) {
        deserializeArray()
    } else {
        val nestedTableHierarchy = extractNestedTableHierarchy(isFormatV2)
        val nestedRows: Array<Array<Any>> = map { arrayNode ->
            arrayNode.extractValues(nestedTableHierarchy.columnChildren, isFormatV2).toTypedArray()
        }.toTypedArray()
        ArrayBackedNestedTable(nestedRows, nestedTableHierarchy)
    }
}

/**
 *  Determines whether the given [JsonNode] represents a multidimensional array of primitives.
 *  It is safe to check only on the top level because of the way Dataframe serialization works.
 *	It is impossible for objects to be mixed with primitives on any level except the top one.
 */
private fun JsonNode.isMultidimensionalArrayOfPrimitives(): Boolean = all { !it.isObject }

private fun JsonNode.deserializeValue(): Any {
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

private fun JsonNode.deserializeArray(): List<*> {
    return map { if (it.isArray) it.deserializeArray() else it.deserializeValue() }
}

private fun JsonNode.extractNestedTableHierarchy(isFormatV2: Boolean): ColumnTreeNode {
    return find { it.isObject }?.extractHierarchy(isFormatV2) ?: createRoot()
}

private fun JsonNode.getByPath(path: List<String>): JsonNode {
    var result: JsonNode = this
    for (field in path) {
        result = result.get(field)
    }

    return result
}

private fun JsonNode.isColumnGroup(): Boolean {
    return has(METADATA_FIELD) && this[METADATA_FIELD][COLUMN_KIND_FIELD].asText() == COLUMN_GROUP
}

private fun JsonNode.isFrame(): Boolean {
    return has(METADATA_FIELD) && this[METADATA_FIELD][COLUMN_KIND_FIELD].asText() == FRAME_COLUMN
}

private fun createRoot() = ColumnTreeNode("root", -1, 0, mutableListOf())

internal fun createRoot(childrenNames: List<String>): ColumnTreeNode {
    val root = createRoot()
    for ((index, column) in childrenNames.withIndex()) {
        root.columnChildren.add(ColumnTreeNode(column, index, index, mutableListOf()))
    }

    return root
}
