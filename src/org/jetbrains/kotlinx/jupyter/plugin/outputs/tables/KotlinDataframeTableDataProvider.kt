// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.tables

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.lang.Language
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.debugger.pydev.TableCommandType
import com.jetbrains.python.debugger.pydev.tables.CommandOutputType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.Companion.columnsField
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.Companion.nColsField
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.Companion.nRowsField
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.Companion.separator
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing.Companion.serializedDataframeField
import org.jetbrains.plugins.notebooks.tables.DSTableBundle
import org.jetbrains.plugins.notebooks.tables.DSTableData
import org.jetbrains.plugins.notebooks.tables.DataId
import org.jetbrains.plugins.notebooks.tables.ExternalTableDataProviderFactory
import org.jetbrains.plugins.notebooks.tables.api.DSDataFrameInfo
import org.jetbrains.plugins.notebooks.tables.api.DSTableDataProvider
import org.jetbrains.plugins.notebooks.tables.api.DSTableText
import org.jetbrains.plugins.notebooks.tables.py.DSTableDataType
import org.jetbrains.plugins.notebooks.tables.DSTableDataException
import org.jetbrains.plugins.notebooks.tables.api.DSTableCommandExecutor
import javax.swing.RowSorter
import javax.swing.SortOrder

class KotlinDataframeTableDataProvider : ExternalTableDataProviderFactory {
    override fun isTableDataFormatSupported(text: DSTableText): Boolean {
        return isSwingUiEnabledForKotlinDataframe() &&
                KotlinDataframeParsing.isKotlinDataFrame(text)
    }

    override fun isTableDataFormatSupported(messageContentData: ObjectNode): Boolean {
        return isSwingUiEnabledForKotlinDataframe() &&
                KotlinDataframeParsing.isKotlinDataFrame(messageContentData)
    }

    override fun getDataProvider(): DSTableDataProvider = KotlinDataFrameProvider()

    override fun extractSerializedData(messageContentData: ObjectNode): String = KotlinDataframeParsing.extractSerializedDataFrame(messageContentData)

    override fun isLanguageSupported(lang: Language): Boolean = lang == KotlinLanguage.INSTANCE
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
        val tableHtml = commandExecutor.executeCommand(
            getSliceCommand(initExpression, commandExecutor.isDisplaySupported(), start, end),
            TableCommandType.SLICE, CommandOutputType.DISPLAY
        )
        return parseDataFromKotlinDataframeOutput(dataId, tableHtml)
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
        val rawJson = mapper.readTree(text)

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
        val rawJson = mapper.readTree(text)

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

internal fun isSwingUiEnabledForKotlinDataframe(): Boolean = Registry.`is`("kotlin.dataframe.swing.outputs.enabled", false)
