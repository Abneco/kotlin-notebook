// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.tables

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn
import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.tables.i18n.KotlinNotebookTablesBundle
import com.intellij.notebooks.dataframe.DataFrameParsingNotifications
import com.intellij.notebooks.dataframe.KotlinDataFrameProviderBase
import com.intellij.notebooks.dataframe.KotlinDataframeParser
import com.intellij.notebooks.dataframe.newKDFObjectMapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.scientific.tables.api.DSDataFrameInfo
import com.intellij.scientific.tables.api.DSTableCommandExecutor
import com.intellij.scientific.tables.api.DSTableDataProvider
import com.intellij.scientific.tables.api.DSTableDataType
import com.intellij.scientific.tables.api.DSTableRawData
import com.intellij.scientific.tables.api.DSTableText
import com.intellij.scientific.tables.api.DataId
import com.intellij.scientific.tables.api.TableDataProviderFactory
import com.intellij.scientific.tables.api.TableDataTypeDetector
import com.intellij.scientific.tables.api.command.ImageCommand
import com.intellij.scientific.tables.api.command.InfoTableCommand
import com.intellij.scientific.tables.api.command.InspectionsTableCommand
import com.intellij.scientific.tables.api.command.SliceTableCommand
import com.intellij.scientific.tables.api.command.StatisticsTableCommand
import com.intellij.scientific.tables.api.command.TableCommand
import com.intellij.scientific.tables.utils.exceptions.DSTableDataException
import com.intellij.scientific.tables.utils.exceptions.DSTableLoadingInterruptedException
import javax.swing.RowSorter
import javax.swing.SortOrder

internal const val DEFAULT_JSON_MAX_LENGTH = 100_000_000

private const val JSON_MAX_STRING_LENGTH = "jupyter.notebook.json.maxStringLength"

class KotlinDataframeTableDataProvider : TableDataProviderFactory, TableDataTypeDetector {
    override fun getTableDataProvider(project: Project, type: DSTableDataType, text: DSTableText): DSTableDataProvider? {
        if (type != DSTableDataType.EXTERNAL) return null
        val plainText = text.plainText
        return getDataProviderCapableToParseData(project, plainText)
    }

    override fun detectTableType(dataObject: ObjectNode, hasOutputInCurrentSession: Boolean): DSTableDataType? {
        return if (isFormatSupported(dataObject.toString())) DSTableDataType.EXTERNAL
        else null
    }

    private fun isFormatSupported(serializedData: String?): Boolean {
        return KotlinNotebookApplicationOptions.get().showDataFrameAsSwing &&
               serializedData != null &&
               KotlinDataframeParsing.isFormatSupported(serializedData)
    }

    fun getDataProviderCapableToParseData(project: Project, serializedData: String): DSTableDataProvider {
        val mapper = newKDFObjectMapper(JSON_MAX_STRING_LENGTH, DEFAULT_JSON_MAX_LENGTH)
        val parser = KotlinDataframeParsing.createParserForData(serializedData, mapper)
        val columnsLimitFromRegistry = Registry.intValue("grid.tables.columns.limit", 2000)

        return KotlinDataFrameProvider(project, parser, columnsLimitFromRegistry)
    }
}

class KotlinDataFrameProvider(project: Project, parser: KotlinDataframeParser, columnsLimit: Int) :
    KotlinDataFrameProviderBase(project, parser, columnsLimit, JupyterDataFrameParsingNotifications) {
    @Throws(DSTableDataException::class)
    override suspend fun loadDynamicTableDataFrameInfo(
        commandExecutor: DSTableCommandExecutor,
        tableVariable: String,
        textTableOutput: String,
    ): DSDataFrameInfo {
        // Parse data frame data sent in output and prepare
        // basic statics info that can be fetched on request.
        val info = executeParsing(textTableOutput) { parseFrameInfoFromKotlinDataframeOutput(textTableOutput, isPreview = false) }
        val dataStatistics = KotlinTableStatisticsDataImpl(commandExecutor, tableVariable)

        return info.copy(dataStatistics = dataStatistics)
    }

    @Throws(DSTableDataException::class, DSTableLoadingInterruptedException::class)
    override suspend fun loadDynamicTableData(
        commandExecutor: DSTableCommandExecutor,
        dataId: DataId,
        tableVariable: String,
        format: String?,
        start: Int,
        end: Int,
    ): DSTableRawData {
        @NlsSafe
        val response = commandExecutor.executeCommand(
            SliceTableCommand(tableVariable, false, format, start, end),
            ::getCommandCode
        ).getOrElse {
            throw DSTableLoadingInterruptedException("Cannot execute the command", it)
        }

        return executeParsing(response) { parseDataFromKotlinDataframeOutput(dataId, response) }
    }

    override fun getNestedTableCommand(tableVariable: String, path: List<NestedTableCellCoordinate>): String {
        var command = tableVariable
        for (cellCoordinate in path) {
            val column = cellCoordinate.column

            val columnSelector = if (column is HierarchicalGridColumn) {
                column.getFullyQualifiedName().joinToString(separator = "") { "[\"$it\"]" }
            }
            else {
                "[\"${column.name}\"]"
            }

            command = """
                KotlinNotebookPluginUtils.convertToDataFrame($command!!)$columnSelector[${cellCoordinate.rowIdx}]
            """.trimIndent()
        }

        return command
    }

    private fun getCommandCode(tableCommand: TableCommand): String {
        return when (tableCommand) {
            is SliceTableCommand -> {
                getSliceCommandCode(tableCommand)
            }
            is InfoTableCommand, is ImageCommand, is StatisticsTableCommand, is InspectionsTableCommand -> {
                throw UnsupportedOperationException("For Kotlin DataFrame provider only slice command is supported")
            }
        }
    }

    private fun getSliceCommandCode(command: SliceTableCommand): String {
        return with(command) {
            """
                try {
                    DISPLAY(KotlinNotebookPluginUtils.getRowsSubsetForRendering($tableVariable, $startRow, $endRow), "")
                } catch (_: IllegalArgumentException) {
                    DISPLAY(($tableVariable)!!, "")
                }
            """.trimIndent()
        }
    }

    override suspend fun getSortingCommand(
        tableVariable: String,
        sortKeys: List<RowSorter.SortKey>,
        columns: List<String>,
        indexColumnWidth: Int,
    ): String {
        if (sortKeys.isEmpty()) return tableVariable

        if (columns.isEmpty()) return tableVariable

        if (columns.all { it.isBlank() }) return tableVariable

        val columnPaths = sortKeys
            .map {
                val name = columns[it.column]
                val path = name.split(".").joinToString { "\"$it\"" }
                "listOf($path)"
            }

        val orderings = sortKeys.map { it.sortOrder == SortOrder.DESCENDING }

        return """
            KotlinNotebookPluginUtils.sortByColumns(KotlinNotebookPluginUtils.convertToDataFrame(${tableVariable}!!), listOf(${columnPaths.joinToString()}), listOf(${orderings.joinToString()}))
            """.trimIndent()
    }
}

private object JupyterDataFrameParsingNotifications : DataFrameParsingNotifications() {
    override val notificationGroupId: String = "Kotlin Notebook output error"

    override val cannotParseDataFrame: String =
        KotlinNotebookTablesBundle.message("kotlin.jupyter.table.output.cannot.parse.dataframe.error")

    override fun tooManyColumnsContent(numberOfColumns: Int): String =
        KotlinNotebookTablesBundle.message(
            "kotlin.jupyter.table.output.notification.content.could.not.display.table.with.d.columns",
            numberOfColumns
        )
}