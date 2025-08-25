// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.tables

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.core.exc.StreamConstraintsException
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn
import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.tables.i18n.KotlinNotebookTablesBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.scientific.tables.DSTableBundle
import com.intellij.scientific.tables.DSTableDataException
import com.intellij.scientific.tables.DSTableRawData
import com.intellij.scientific.tables.DataId
import com.intellij.scientific.tables.api.DSDataFrameInfo
import com.intellij.scientific.tables.api.DSTableCommandExecutor
import com.intellij.scientific.tables.api.DSTableDataProvider
import com.intellij.scientific.tables.api.DSTableDataType
import com.intellij.scientific.tables.api.DSTableText
import com.intellij.scientific.tables.api.NestedTableDataProvider
import com.intellij.scientific.tables.api.TableDataProviderFactory
import com.intellij.scientific.tables.api.TableDataTypeDetector
import com.intellij.scientific.tables.api.command.ImageCommand
import com.intellij.scientific.tables.api.command.InfoTableCommand
import com.intellij.scientific.tables.api.command.InspectionsTableCommand
import com.intellij.scientific.tables.api.command.SliceTableCommand
import com.intellij.scientific.tables.api.command.StatisticsTableCommand
import com.intellij.scientific.tables.api.command.TableCommand
import com.intellij.scientific.tables.api.filters.FilterExpression
import com.intellij.scientific.tables.utils.launchEdt
import java.io.IOException
import javax.swing.RowSorter
import javax.swing.SortOrder

internal const val DEFAULT_JSON_MAX_LENGTH = 100000000

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
        val jsonFactory = JsonFactory()
        jsonFactory.setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxStringLength(Registry.intValue(JSON_MAX_STRING_LENGTH, DEFAULT_JSON_MAX_LENGTH))
                .build()
        )
        val mapper = ObjectMapper(jsonFactory)
        mapper.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature())

        val parser = KotlinDataframeParsing.createParserForData(serializedData, mapper)
        val columnsLimitFromRegistry = Registry.intValue("grid.tables.columns.limit", 2000)

        return KotlinDataFrameProvider(project, parser, columnsLimitFromRegistry)
    }
}

const val NULL: String = "null"

class KotlinDataFrameProvider(private val project: Project, private val parser: KotlinDataframeParser, private val columnsLimit: Int) : NestedTableDataProvider {
    override fun getType(): DSTableDataType = DSTableDataType.EXTERNAL

    override suspend fun parseStaticTableToFrameInfo(text: String): DSDataFrameInfo {
        return parseFrameInfoFromKotlinDataframeOutput(text, isPreview = true)
    }

    override suspend fun parseStaticTableToTableData(dataId: DataId, table: String): DSTableRawData {
        return parseDataFromKotlinDataframeOutput(dataId, table)
    }

    @Throws(DSTableDataException::class)
    override suspend fun loadDynamicTableDataFrameInfo(
        commandExecutor: DSTableCommandExecutor,
        tableVariable: String,
        textTableOutput: String
    ): DSDataFrameInfo {
        // Parse data frame data sent in output and prepare
        // basic statics info that can be fetched on request.
        val info = executeParsing(textTableOutput) { parseFrameInfoFromKotlinDataframeOutput(textTableOutput, isPreview = false) }
        val dataStatistics = KotlinTableStatisticsDataImpl(commandExecutor, tableVariable)

        return info.copy(dataStatistics = dataStatistics)
    }

    @Throws(DSTableDataException::class)
    override suspend fun loadDynamicTableData(
        commandExecutor: DSTableCommandExecutor,
        dataId: DataId,
        tableVariable: String,
        format: String?,
        start: Int,
        end: Int
    ): DSTableRawData {
        @NlsSafe
        val response = commandExecutor.executeCommand(
            SliceTableCommand(tableVariable, false, format, start, end),
            ::getCommandCode
        ).getOrThrow()

        return executeParsing(response) { parseDataFromKotlinDataframeOutput(dataId, response) }
    }

    @Throws(DSTableDataException::class)
    private inline fun <T> executeParsing(@NlsSafe textData: String, parseFunction: () -> T): T {
        return try {
            parseFunction()
        } catch (e: DSTableDataException) {
            throw e
        } catch (e: JsonParseException) {
            // should be removed after KTNB-385 and KTNB-384
            if (isNonComparableColumnSortingError(textData)) {
                launchEdt {
                    serviceAsync<NotificationGroupManager>().getNotificationGroup("Kotlin Notebook output error")
                        .createNotification(
                            KotlinNotebookTablesBundle.message(
                                "kotlin.jupyter.table.output.sort_column_not_comparable.error",
                                extractColumnNameFromSortErrorMessage(textData)
                            ),
                            extractNonComparableColumnTypeMessage(textData)
                                ?: KotlinNotebookTablesBundle.message("kotlin.jupyter.table.output.sort_column_not_comparable.error.message"),
                            NotificationType.WARNING
                        )
                        .notify(project)
                }
                throw DSTableDataException(KotlinNotebookTablesBundle.message("kotlin.jupyter.table.output.sort_column_not_comparable.error.message"), e)
            }

            notifyUnknownParsingException()
            throw DSTableDataException("Error parsing data from Kotlin DataFrame output. Reason: ${e.localizedMessage}", e)
        } catch (e: StreamConstraintsException) {
            // users should not encounter this error anymore once KTNB-272 is implemented.
            launchEdt {
                serviceAsync<NotificationGroupManager>().getNotificationGroup("Kotlin Notebook output error")
                    .createNotification(
                        KotlinNotebookTablesBundle.message("kotlin.jupyter.table.output.cannot.render.dataframe.error"),
                        KotlinNotebookTablesBundle.message("kotlin.jupyter.table.output.cannot.parse.dataframe.error"),
                        NotificationType.WARNING
                    )
                    .notify(project)
            }
            throw DSTableDataException("Error parsing data from Kotlin DataFrame output. Reason: ${e.localizedMessage}", e)
        } catch (e: IOException) {
            notifyUnknownParsingException()
            throw DSTableDataException("Error parsing data from Kotlin DataFrame output. Reason: ${e.localizedMessage}", e)
        } catch (e: RuntimeException) {
            notifyUnknownParsingException()
            throw DSTableDataException("Error parsing data from Kotlin DataFrame output. Reason: ${e.localizedMessage}", e)
        }
    }

    @NlsSafe
    private fun extractNonComparableColumnTypeMessage(exceptionMessage: String): String? {
        val regex = Regex("""Column '(.+?)' has type '(.+?)' that is not Comparable""")
        val matchResult = regex.find(exceptionMessage)
        return matchResult?.value
    }

    private fun notifyUnknownParsingException() {
        NotificationGroupManager.getInstance().getNotificationGroup("Kotlin Notebook output error")
            .createNotification(
                KotlinNotebookTablesBundle.message("kotlin.jupyter.table.output.cannot.render.dataframe.error"),
                KotlinNotebookTablesBundle.message("kotlin.jupyter.table.output.cannot.parse.dataframe.error.unknown"),
                NotificationType.WARNING
            )
            .notify(project)
    }

    override fun getNestedTableCommand(tableVariable: String, path: List<NestedTableCellCoordinate>): String {
        var command = tableVariable
        for (cellCoordinate in path) {
            val column = cellCoordinate.column

            val columnSelector = if (column is HierarchicalGridColumn) {
                column.getFullyQualifiedName().joinToString(separator = "") { "[\"$it\"]" }
            } else {
                "[\"${column.name}\"]"
            }

            command = """
                KotlinNotebookPluginUtils.convertToDataFrame($command!!)$columnSelector[${cellCoordinate.rowIdx}]
            """.trimIndent()
        }

        return command
    }

    private fun isNonComparableColumnSortingError(
        response: String,
        errorIndicators: List<String> = listOf("Column", "has type", "that is not Comparable")
    ): Boolean {
        return errorIndicators.all { indicator -> response.contains(indicator) }
    }

    private fun extractColumnNameFromSortErrorMessage(errorMsg: String): String {
        val regex = "Column '+(.*?)'+".toRegex()
        val matchResult = regex.find(errorMsg)
        return matchResult?.groupValues?.get(1) ?: ""
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
        indexColumnWidth: Int
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

    override suspend fun getFilteringCommand(
        tableVariable: String,
        filters: FilterExpression?,
        tableColumnsNumber: Int
    ): String {
        return tableVariable
    }

    override suspend fun isFallbackToStaticTableSupported(): Boolean = true

    private fun parseFrameInfoFromKotlinDataframeOutput(text: String, isPreview: Boolean): DSDataFrameInfo {
        val info = parser.parseDataFrameInfo(text).asDsTableInfo(isPreview)
        requireNumberOfColumnsLessThenLimit(info.columnNames.size)

        return info
    }

    private fun parseDataFromKotlinDataframeOutput(id: DataId, text: String): DSTableRawData {
        val columnValues = parser.parseDataFrameData(text)
        requireNumberOfColumnsLessThenLimit(columnValues.size)

        return DSTableRawData(id, columnValues)
    }

    private fun requireNumberOfColumnsLessThenLimit(numberOfColumns: Int) {
        if (numberOfColumns > columnsLimit) {
            NotificationGroupManager.getInstance().getNotificationGroup("Kotlin Notebook output error")
                .createNotification(
                    KotlinNotebookTablesBundle.message("kotlin.jupyter.table.output.too.many.columns.error"),
                    KotlinNotebookTablesBundle.message("kotlin.jupyter.table.output.notification.content.could.not.display.table.with.d.columns", numberOfColumns),
                    NotificationType.WARNING
                )
                .notify(project)
            throw DSTableDataException("Attempt to create grid with ${numberOfColumns} columns")
        }
    }
}

private fun KotlinDataframeInfo.asDsTableInfo(isPreview: Boolean): DSDataFrameInfo {
    if (rowsNum == 0) {
        return DSDataFrameInfo(
            0,
            0,
            topLevelColumnNames,
            topLevelTypeNames,
            DSTableBundle.message("ds.table.dimensions.info", 0, 0),
            hierarchyRoot = columnTreeRoot,
            tableType = DSTableDataType.KOTLIN_DATAFRAME
        )
    }

    val nRow = if (isPreview) rowsNum else totalRowsNum
    val nCol = topLevelColumnNames.size

    val dimensionsStr = DSTableBundle.message("ds.table.dimensions.info", nRow, nCol)

    return DSDataFrameInfo(
        nRow,
        0,
        topLevelColumnNames,
        topLevelTypeNames,
        dimensionsStr,
        hierarchyRoot = columnTreeRoot,
        tableType = DSTableDataType.KOTLIN_DATAFRAME
    )
}