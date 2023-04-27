// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.tables

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.containers.JBTreeTraverser
import com.jetbrains.python.debugger.pydev.TableCommandType
import com.jetbrains.python.debugger.pydev.tables.CommandOutputType
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.columnsField
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.jsonPayloadField
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.nColsField
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.nRowsField
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.separator
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.serializedDataframeField
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

    override val pydevdId: String
        get() = TODO("Makes no sense for Kotlin and should be removed")

    override fun parseTextToFrameInfo(text: String): DSDataFrameInfo {
        return parseFrameInfoFromKotlinDataframeOutput(text)
    }

    override fun parseTextToTableData(id: DataId, tableHtml: String): DSTableData {
        return parseDataFromKotlinDataframeOutput(id, tableHtml)
    }

    @Throws(DSTableDataException::class)
    override fun getTableInfo(
        commandExecutor: DSTableCommandExecutor,
        initialCommand: String,
        textTableOutput: String?
    ): DSDataFrameInfo {
        return parseFrameInfoFromKotlinDataframeOutput(textTableOutput!!)
    }

    override fun dataFrameGetData(
        commandExecutor: DSTableCommandExecutor,
        dataId: DataId,
        initExpression: String,
        start: Int,
        end: Int
    ): DSTableData {
        val tableText = commandExecutor.executeCommand(
            getSliceCommand(initExpression, commandExecutor.isDisplaySupported(), start, end),
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

    override fun getSortingCommand(initCommand: String, sortKeys: List<RowSorter.SortKey>, columns: List<String>): String {
        if (columns.isEmpty()) return initCommand

        val kotlinDataframeSortKeys = sortKeys
            .map { "\"${columns[it.column]}\"${if (it.sortOrder == SortOrder.DESCENDING) ".desc()" else ""}" }
            .toMutableList()

        if (sortKeys.size == 1 && sortKeys[0].sortOrder != SortOrder.DESCENDING) {
            kotlinDataframeSortKeys.add(kotlinDataframeSortKeys[0])
        }

        return "(($initCommand as DataFrame<*>).sortBy { ${kotlinDataframeSortKeys.joinToString(" and ")} })"
    }

    override fun isFallbackToTruncatedSupported(): Boolean = true

    private fun parseFrameInfoFromKotlinDataframeOutput(text: String): DSDataFrameInfo {
        val data = mapper.readTree(text)
        val rawJson = mapper.readTree(data[jsonPayloadField].asText())

        val rawRows = asConcatenatedRows(rawJson[serializedDataframeField])
        val rows = rawRows.split(separator)
        val firstRowJson = mapper.readTree(rows[0])

        val columnTreeTraverser = extractHierarchy(firstRowJson)
        val columnNames = getLeafColumnNames(columnTreeTraverser)

        val nRow = rawJson[nRowsField].asInt()
        val nCol = rawJson[nColsField].asInt()

        val dimensionsStr = DSTableBundle.message("ds.table.dimensions.info", nRow, nCol)

        return DSDataFrameInfo(
            nRow,
            0,
            columnNames,
            List(columnNames.size) { null },
            dimensionsStr,
            columnsTreeTraverser = columnTreeTraverser
        )
    }

    private fun extractHierarchy(row: JsonNode): JBTreeTraverser<ColumnTreeNode> {
        val root = ColumnTreeNode("root", -1, mutableListOf())

        var index = 0
        fun extractColumnsHelper(jsonNode: JsonNode, columnsNode: ColumnTreeNode, path: List<String>) {
            if (jsonNode.isObject) {
                jsonNode.fields().forEach { (key, value) ->
                    val child = ColumnTreeNode(key, index++, mutableListOf())
                    columnsNode.children.add(child)
                    extractColumnsHelper(value, child, path + listOf(key))
                }
            }
        }

        extractColumnsHelper(row, root, emptyList())

        return JBTreeTraverser
            .from<ColumnTreeNode?> { node -> node.children }
            .withRoots(root.children)
    }

    private fun getColumnPathsToValues(traverser: JBTreeTraverser<ColumnTreeNode>): List<List<String>> {
        val paths = mutableListOf<List<String>>()
        val stack = mutableListOf<ColumnTreeNode>()

        for (node in traverser.preOrderDfsTraversal()) {
            while (stack.isNotEmpty() && !stack.last().isParent(node)) {
                stack.removeLast()
            }

            stack.add(node)

            if (isLeaf(node)) {
                paths.add(stack.map { it.name })
            }
        }

        return paths
    }

    private fun ColumnTreeNode.isParent(node: ColumnTreeNode): Boolean {
        return children.contains(node)
    }

    private fun isLeaf(node: ColumnTreeNode): Boolean {
        return node.children.isEmpty()
    }

    private fun getLeafColumnNames(traverser: JBTreeTraverser<ColumnTreeNode>): List<String> {
        val leafColumnNames = mutableListOf<String>()

        for (node in traverser.preOrderDfsTraversal()) {
            if (node.children.isEmpty()) {
                leafColumnNames.add(node.name)
            }
        }

        return leafColumnNames
    }

    private fun parseDataFromKotlinDataframeOutput(id: DataId, text: String): DSTableData {
        val data = mapper.readTree(text)
        val rawJson = mapper.readTree(data[jsonPayloadField].asText())

        val rawRows = asConcatenatedRows(rawJson[serializedDataframeField])
        val rows = rawRows.split(separator)
        val firstRowJson = mapper.readTree(rows[0])

        val columnTreeTraverser = extractHierarchy(firstRowJson)
        val columnPaths = getColumnPathsToValues(columnTreeTraverser)

        val columnValues = List(columnPaths.size) { mutableListOf<String>() }

        for (row in rows) {
            val json = mapper.readTree(row)
            columnPaths.forEachIndexed { idx, path ->
                val columnValue = extractNestedValue(json, path)
                columnValues[idx].add(columnValue?.asText() ?: "")
            }
        }

        return DSTableData(id, columnValues)
    }

    private fun extractNestedValue(node: JsonNode, keys: List<String>): JsonNode? {
        var current = node

        for (key in keys) {
            current = current[key] ?: return null
        }

        return current
    }

    private fun asConcatenatedRows(text: JsonNode): String {
        return if (text.isArray) {
            (text as ArrayNode).joinToString(separator = separator)
        } else {
            text.asText()
        }
    }
}
