// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.tables

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.debugger.pydev.TableCommandType
import com.jetbrains.python.debugger.pydev.tables.CommandOutputType
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.columnsField
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.jsonPayloadField
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.nColsField
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.nRowsField
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.separator
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.serializedDataframeField
import org.jetbrains.plugins.notebooks.tables.DSTableBundle
import org.jetbrains.plugins.notebooks.tables.DSTableData
import org.jetbrains.plugins.notebooks.tables.DataId
import org.jetbrains.plugins.notebooks.tables.ExternalTableDataProviderFactory
import org.jetbrains.plugins.notebooks.tables.api.DSDataFrameInfo
import org.jetbrains.plugins.notebooks.tables.api.DSTableDataProvider
import org.jetbrains.plugins.notebooks.tables.py.DSTableDataType
import org.jetbrains.plugins.notebooks.tables.DSTableDataException
import org.jetbrains.plugins.notebooks.tables.api.DSTableCommandExecutor
import javax.swing.RowSorter
import javax.swing.SortOrder


internal val isSwingUiEnabledForKotlinDataframe: Boolean
    get() = Registry.`is`("kotlin.dataframe.swing.outputs.enabled", false)

class KotlinDataframeTableDataProvider : ExternalTableDataProviderFactory {
    override fun getDataProviderWhichSupportsFormatOrNull(serializedData: String?): DSTableDataProvider? {
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

    override fun parseTextToTableData(id: DataId, tableHtml: String): DSTableData {
        return parseDataFromKotlinDataframeOutput(id, tableHtml)
    }

    @Throws(DSTableDataException::class)
    override fun dataFrameInfo(
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

        val nRow = rawJson[nRowsField].asInt()
        val nCol = rawJson[nColsField].asInt()
        val columnNames = mutableListOf<String>()
        (rawJson[columnsField] as ArrayNode).elements().forEach {
            columnNames.add(it.asText())
        }
        val dimensionsStr = DSTableBundle.message("ds.table.dimensions.info", nRow, nCol)

        return DSDataFrameInfo(nRow, 0, columnNames, dimensionsStr)
    }

    private fun parseDataFromKotlinDataframeOutput(id: DataId, text: String): DSTableData {
        val data = mapper.readTree(text)
        val rawJson = mapper.readTree(data[jsonPayloadField].asText())

        val rawRows = asConcatenatedRows(rawJson[serializedDataframeField])
        val rows = rawRows.split(separator)
        val rowJson = mapper.readTree(rows[0])
        val keys: MutableList<String> = ArrayList()
        val iterator = rowJson.fieldNames()
        iterator.forEachRemaining { e: String -> keys.add(e) }

        val cols = List(keys.size) { mutableListOf<String>() }

        for (row in rows) {
            val json = mapper.readTree(row)
            keys.forEachIndexed { idx, key -> cols[idx].add(json[key]?.asText() ?: "") }
        }

        return DSTableData(id, cols)
    }

    private fun asConcatenatedRows(text: JsonNode): String {
        return if (text.isArray) {
            (text as ArrayNode).joinToString(separator = separator)
        } else {
            text.asText()
        }
    }
}
