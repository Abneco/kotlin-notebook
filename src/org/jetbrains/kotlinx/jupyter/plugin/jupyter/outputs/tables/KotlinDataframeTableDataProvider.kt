// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.intellij.database.datagrid.ArrayBackedNestedTable
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.containers.tail
import com.jetbrains.python.debugger.pydev.TableCommandType
import com.jetbrains.python.debugger.pydev.tables.CommandOutputType
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.columnsField
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.jsonPayloadField
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.nColsField
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.nRowsField
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.separator
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing.serializedDataframeField
import org.jetbrains.plugins.notebooks.tables.ColumnTreeNode
import org.jetbrains.plugins.notebooks.tables.DSTableBundle
import org.jetbrains.plugins.notebooks.tables.DSTableData
import org.jetbrains.plugins.notebooks.tables.DSTableDataException
import org.jetbrains.plugins.notebooks.tables.DataId
import org.jetbrains.plugins.notebooks.tables.ExternalTableDataProviderFactory
import org.jetbrains.plugins.notebooks.tables.api.DSDataFrameInfo
import org.jetbrains.plugins.notebooks.tables.api.DSTableCommandExecutor
import org.jetbrains.plugins.notebooks.tables.api.DSTableDataProvider
import org.jetbrains.plugins.notebooks.tables.py.DSTableDataType
import java.util.*
import javax.swing.RowSorter
import javax.swing.SortOrder


internal val isSwingUiEnabledForKotlinDataframe: Boolean
    // it's implemented this way to make it possible to set the registry key programmatically for tests
    get() = try {
        Registry.`is`("kotlin.dataframe.swing.outputs.enabled")
    } catch (e: MissingResourceException) {
        false
    }

class KotlinDataframeTableDataProvider : ExternalTableDataProviderFactory {
    override fun getDataProviderCapableToParseDataOrNull(serializedData: String?): DSTableDataProvider? {
        if (!isSwingUiEnabledForKotlinDataframe) return null
        if (serializedData == null || !isFormatSupported(serializedData)) return null

        return KotlinDataFrameProvider()
    }

    private fun isFormatSupported(serializedData: String): Boolean {
        return serializedData.contains(serializedDataframeField) &&
                serializedData.contains(nColsField) &&
                serializedData.contains(nRowsField) &&
                serializedData.contains(columnsField)
    }
}

class KotlinDataFrameProvider(private val mapper: ObjectMapper = ObjectMapper()) : DSTableDataProvider {
    override val type: DSTableDataType = DSTableDataType.EXTERNAL

    override fun parseTextToFrameInfo(text: String): DSDataFrameInfo {
        return parseFrameInfoFromKotlinDataframeOutput(text)
    }

    override fun parseTextToTableData(id: DataId, table: String): DSTableData {
        return parseDataFromKotlinDataframeOutput(id, table)
    }

    @Throws(DSTableDataException::class)
    override fun getTableInfo(
        commandExecutor: DSTableCommandExecutor,
        tableVariable: String,
        textTableOutput: String
    ): DSDataFrameInfo {
        return parseFrameInfoFromKotlinDataframeOutput(textTableOutput)
    }

    override fun dataFrameGetData(
        commandExecutor: DSTableCommandExecutor,
        dataId: DataId,
        tableVariable: String,
        start: Int,
        end: Int
    ): DSTableData {
        val tableText = commandExecutor.executeCommand(
            getSliceCommand(tableVariable, commandExecutor.isDisplaySupported(), start, end),
            TableCommandType.SLICE, CommandOutputType.DISPLAY
        )
        return parseDataFromKotlinDataframeOutput(dataId, tableText)
    }

    private fun getSliceCommand(initCommand: String, isInteractive: Boolean, start: Int, end: Int): String {
        return if (isInteractive) {
            """
            DISPLAY(KotlinNotebookPluginUtils.getRowsSubsetForRendering($initCommand, $start, $end), "")
            """.trimIndent()
        } else {
            initCommand
        }
    }

    override fun getSortingCommand(
        tableVariable: String,
        sortKeys: List<RowSorter.SortKey>,
        columns: List<String>,
        indexColumnWidth: Int
    ): String {
        if (columns.isEmpty()) return tableVariable

        require(columns.all { it.isNotBlank() })

        val kotlinDataframeSortKeys = sortKeys
            .map {
                val name = columns[it.column]
                val nestedNames = name.split(".")
                var sortName = "\"${nestedNames.first()}\""
                for (nestedName in nestedNames.tail()) {
                    sortName += "[\"$nestedName\"]"
                }
                "$sortName${if (it.sortOrder == SortOrder.DESCENDING) ".desc()" else ""}"
            }
            .toMutableList()

        if (sortKeys.size == 1 && sortKeys[0].sortOrder != SortOrder.DESCENDING) {
            kotlinDataframeSortKeys.add(kotlinDataframeSortKeys[0])
        }

        return "(($tableVariable as DataFrame<*>).sortBy { ${kotlinDataframeSortKeys.joinToString(" and ")} })"
    }

    override fun isFallbackToTruncatedSupported(): Boolean = true

    private fun parseFrameInfoFromKotlinDataframeOutput(text: String): DSDataFrameInfo {
        val data = mapper.readTree(text)
        val rawJson = mapper.readTree(data[jsonPayloadField].asText())

        val rawRows = asConcatenatedRows(rawJson[serializedDataframeField])
        val rows = rawRows.split(separator)
        val firstRowJson = mapper.readTree(rows[0])

        val root = extractHierarchy(firstRowJson)
        val columnNames = root.columnChildren.map { it.columnName }

        val nRow = rawJson[nRowsField].asInt()
        val nCol = rawJson[nColsField].asInt()

        val dimensionsStr = DSTableBundle.message("ds.table.dimensions.info", nRow, nCol)

        return DSDataFrameInfo(
            nRow,
            0,
            columnNames,
            List(columnNames.size) { null },
            dimensionsStr,
            hierarchyRoot = root
        )
    }

    private fun extractHierarchy(row: JsonNode): ColumnTreeNode {
        val root = createRoot()

        var index = 0
        fun extractColumnsHelper(jsonNode: JsonNode, columnsNode: ColumnTreeNode, path: List<String>) {
            var childIdx = 0
            if (jsonNode.isObject) {
                jsonNode.fields().forEach { (key, value) ->
                    val child = ColumnTreeNode(key, index++, childIdx++, mutableListOf())
                    columnsNode.columnChildren.add(child)
                    extractColumnsHelper(value, child, path + listOf(key))
                }
            }
        }

        extractColumnsHelper(row, root, emptyList())

        return root
    }

    private fun createRoot() = ColumnTreeNode("root", -1, 0, mutableListOf())

    private fun extractValues(jsonNode: JsonNode, columns: List<ColumnTreeNode>): List<Any> {
        val resultList = mutableListOf<Any>()

        for (column in columns) {
            val columnName = column.name
            val value = jsonNode.get(columnName)

            if (value == null) continue

            when {
                value.isValueNode -> {
                    resultList.add(value.asText())
                }
                value.isObject -> {
                    resultList.add(extractValues(value, column.columnChildren))
                }
                value.isArray -> {
                    var nestedTableHierarchy: ColumnTreeNode? = null
                    val nestedRows: Array<Array<Any>> = value.map { arrayNode ->
                        if (!arrayNode.isObject) return@map arrayOf<Any>()
                        if (nestedTableHierarchy == null) {
                            nestedTableHierarchy = extractHierarchy(arrayNode)
                        }
                        extractValues(arrayNode, nestedTableHierarchy!!.columnChildren).toTypedArray()
                    }.toTypedArray()
                    resultList.add(ArrayBackedNestedTable(nestedRows, nestedTableHierarchy ?: createRoot()))
                }
            }
        }

        return resultList
    }

    private fun parseDataFromKotlinDataframeOutput(id: DataId, text: String): DSTableData {
        val data = mapper.readTree(text)
        val rawJson = mapper.readTree(data[jsonPayloadField].asText())

        val rawRows = asConcatenatedRows(rawJson[serializedDataframeField])
        val rows = rawRows.split(separator)
        val firstRowJson = mapper.readTree(rows[0])

        val root = extractHierarchy(firstRowJson)

        val columnValues = List(root.columnChildren.size) { mutableListOf<Any>() }

        for (row in rows) {
            val json = mapper.readTree(row)
            val values = extractValues(json, root.columnChildren)
            values.forEachIndexed { index, any -> columnValues[index].add(any) }
        }

        return DSTableData(id, columnValues)
    }

    private fun asConcatenatedRows(text: JsonNode): String {
        return if (text.isArray) {
            (text as ArrayNode).joinToString(separator = separator)
        } else {
            text.asText()
        }
    }
}
