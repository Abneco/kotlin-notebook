// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.core.exc.StreamConstraintsException
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn
import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.containers.tail
import com.jetbrains.python.debugger.pydev.TableCommandType
import com.jetbrains.python.debugger.pydev.tables.CommandOutputType
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptions
import org.jetbrains.plugins.notebooks.tables.DSTableBundle
import org.jetbrains.plugins.notebooks.tables.DSTableData
import org.jetbrains.plugins.notebooks.tables.DSTableDataException
import org.jetbrains.plugins.notebooks.tables.DataId
import org.jetbrains.plugins.notebooks.tables.ExternalTableDataProviderFactory
import org.jetbrains.plugins.notebooks.tables.api.DSDataFrameInfo
import org.jetbrains.plugins.notebooks.tables.api.DSTableCommandExecutor
import org.jetbrains.plugins.notebooks.tables.api.DSTableDataProvider
import org.jetbrains.plugins.notebooks.tables.api.DSTableDataType
import org.jetbrains.plugins.notebooks.tables.api.NestedTableDataProvider
import java.io.IOException
import java.util.*
import javax.swing.RowSorter
import javax.swing.SortOrder


internal const val DEFAULT_JSON_MAX_LENGTH = 100000000

private const val JSON_MAX_STRING_LENGTH = "jupyter.notebook.json.maxStringLength"

class KotlinDataframeTableDataProvider : ExternalTableDataProviderFactory {
    override fun getDataProviderCapableToParseDataOrNull(serializedData: String?): DSTableDataProvider? {
        if (!KotlinNotebookApplicationOptions.get().showDataFrameAsSwing) return null
        if (serializedData == null || !KotlinDataframeParsing.isFormatSupported(serializedData)) return null

        val jsonFactory = JsonFactory()
        jsonFactory.setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxStringLength(Registry.intValue(JSON_MAX_STRING_LENGTH, DEFAULT_JSON_MAX_LENGTH))
                .build()
        )
        val mapper = ObjectMapper(jsonFactory)
        mapper.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature())

        val parser = KotlinDataframeParsing.createParserForData(serializedData, mapper)

        return KotlinDataFrameProvider(parser)
    }
}

const val NULL: String = "null"

class KotlinDataFrameProvider(private val parser: KotlinDataframeParser) : NestedTableDataProvider {
    override val type: DSTableDataType = DSTableDataType.EXTERNAL

    override fun parseTextToFrameInfo(text: String): DSDataFrameInfo {
        return parseFrameInfoFromKotlinDataframeOutput(text, isPreview = true)
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
        return executeParsing(textTableOutput) { parseFrameInfoFromKotlinDataframeOutput(textTableOutput, isPreview = false) }
    }

    @Throws(DSTableDataException::class)
    override fun dataFrameGetData(
        commandExecutor: DSTableCommandExecutor,
        dataId: DataId,
        tableVariable: String,
        start: Int,
        end: Int
    ): DSTableData {
        @NlsSafe
        val response = commandExecutor.executeCommand(
            getSliceCommand(tableVariable, commandExecutor.isDisplaySupported(), start, end),
            TableCommandType.SLICE, CommandOutputType.DISPLAY
        )

       return executeParsing(response) { parseDataFromKotlinDataframeOutput(dataId, response) }
    }

    @Throws(DSTableDataException::class)
    private inline fun <T> executeParsing(@NlsSafe textData: String, parseFunction: () -> T): T {
        return try {
            parseFunction()
        } catch (e: JsonParseException) {
            // should be removed after KTNB-385 and KTNB-384
            if (isNonComparableColumnSortingError(textData)) {
                NotificationGroupManager.getInstance().getNotificationGroup("Kotlin Notebook output error")
                    .createNotification(
                        KotlinNotebookBundle.message(
                            "kotlin.jupyter.table.output.sort_column_not_comparable.error",
                            extractColumnNameFromSortErrorMessage(textData)
                        ),
                        textData,
                        NotificationType.WARNING
                    )
                    .notify(null)
            }

            notifyUnknownParsingException()
            throw DSTableDataException("Error parsing data from Kotlin DataFrame output. Reason: ${e.localizedMessage}")
        } catch (e: StreamConstraintsException) {
            // users should not encounter this error anymore once KTNB-272 is implemented.
            NotificationGroupManager.getInstance().getNotificationGroup("Kotlin Notebook output error")
                .createNotification(
                    KotlinNotebookBundle.message("kotlin.jupyter.table.output.cannot.render.dataframe.error"),
                    KotlinNotebookBundle.message("kotlin.jupyter.table.output.cannot.parse.dataframe.error"),
                    NotificationType.WARNING
                )
                .notify(null)

            throw DSTableDataException("Error parsing data from Kotlin DataFrame output. Reason: ${e.localizedMessage}")
        } catch (e: IOException) {
            notifyUnknownParsingException()
            throw DSTableDataException("Error parsing data from Kotlin DataFrame output. Reason: ${e.localizedMessage}")
        } catch (e: RuntimeException) {
            notifyUnknownParsingException()
            throw DSTableDataException("Error parsing data from Kotlin DataFrame output. Reason: ${e.localizedMessage}")
        }
    }

    private fun notifyUnknownParsingException() {
        NotificationGroupManager.getInstance().getNotificationGroup("Kotlin Notebook output error")
            .createNotification(
                KotlinNotebookBundle.message("kotlin.jupyter.table.output.cannot.render.dataframe.error"),
                KotlinNotebookBundle.message("kotlin.jupyter.table.output.cannot.parse.dataframe.error.unknown"),
                NotificationType.WARNING
            )
            .notify(null)
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

    private fun HierarchicalGridColumn.getFullyQualifiedName(): List<String> {
        val names = mutableListOf<String>()
        var cur: HierarchicalGridColumn? = this
        while (cur != null) {
            names.add(0, cur.name)
            cur = cur.parent
        }

        return names
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

    private fun getSliceCommand(initCommand: String, isInteractive: Boolean, start: Int, end: Int): String {
        return if (isInteractive) {
            """
            try {
                DISPLAY(KotlinNotebookPluginUtils.getRowsSubsetForRendering($initCommand, $start, $end), "")
            } catch (_: IllegalArgumentException) {
                DISPLAY(($initCommand)!!, "")
            }
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

        if (columns.all { it.isBlank() }) return tableVariable

        val kotlinDataframeSortKeys = sortKeys
            .map {
                val name = columns[it.column]
                val sortName = join(name.split("."))
                "$sortName${if (it.sortOrder == SortOrder.DESCENDING) ".desc()" else ""}"
            }
            .toMutableList()

        if (sortKeys.size == 1 && sortKeys[0].sortOrder != SortOrder.DESCENDING) {
            kotlinDataframeSortKeys.add(kotlinDataframeSortKeys[0])
        }

        // This is a workaround to fix the issue KTNB-382.
        // There is no alternative solution that won’t compromise compatibility with dataframe versions <= 0.12.1.
        // This temporary code should be removed once the majority of users have upgraded to 0.12.1 or a higher version.
        return """
            try{
                (($tableVariable as DataFrame<*>).sortBy { ${kotlinDataframeSortKeys.joinToString(" and ")} })
            } catch (e: Exception) {
                val dataframeLike = ($tableVariable) as Any
                val df = when (dataframeLike) {
                    is Pivot<*> -> dataframeLike.frames().toDataFrame()
                    is ReducedPivot<*> -> dataframeLike.values().toDataFrame()
                    is PivotGroupBy<*> -> dataframeLike.frames()
                    is ReducedPivotGroupBy<*> -> dataframeLike.values()
                    is SplitWithTransform<*, *, *> -> dataframeLike.into()
                    is Merge<*, *, *> -> dataframeLike.into("merged")
                    is Gather<*, *, *, *> -> dataframeLike.into("key", "value")
                    is Update<*, *> -> dataframeLike.df
                    is Convert<*, *> -> dataframeLike.df
                    is AnyCol -> dataFrameOf(dataframeLike)
                    is AnyRow -> dataframeLike.toDataFrame()
                    is GroupBy<*, *> -> dataframeLike.toDataFrame()
                    is AnyFrame -> dataframeLike
                    is RenameClause<*, *> -> dataframeLike.df
                    is ReplaceClause<*, *> -> dataframeLike.df
                    is GroupClause<*, *> -> dataframeLike.into("untitled")
                    is InsertClause<*> -> dataframeLike.at(0)
                    is FormatClause<*, *> -> dataframeLike.df
                    else -> throw IllegalArgumentException("Unsupported type")
                }
                ((df as DataFrame<*>).sortBy { ${kotlinDataframeSortKeys.joinToString(" and ")} })
            }
        """.trimIndent()
    }

    private fun join(nestedNames: List<String>): String {
        var fullName = "\"${nestedNames.first()}\""
        for (nestedName in nestedNames.tail()) {
            fullName += "[\"$nestedName\"]"
        }

        return fullName
    }

    override fun isFallbackToStaticTableSupported(): Boolean = true

    private fun parseFrameInfoFromKotlinDataframeOutput(text: String, isPreview: Boolean): DSDataFrameInfo {
        return parser.parseDataFrameInfo(text).asDsTableInfo(isPreview)
    }

    private fun parseDataFromKotlinDataframeOutput(id: DataId, text: String): DSTableData {
        val columnValues = parser.parseDataFrameData(text)
        return DSTableData(id, columnValues)
    }
}

private fun KotlinDataframeInfo.asDsTableInfo(isPreview: Boolean): DSDataFrameInfo {
    if (rowsNum == 0) {
        return DSDataFrameInfo(
            0,
            0,
            topLevelColumnNames,
            emptyList(),
            DSTableBundle.message("ds.table.dimensions.info", 0, 0),
            hierarchyRoot = columnTreeRoot
        )
    }

    val nRow = if (isPreview) rowsNum else totalRowsNum
    val nCol = topLevelColumnNames.size

    val dimensionsStr = DSTableBundle.message("ds.table.dimensions.info", nRow, nCol)

    return DSDataFrameInfo(
        nRow,
        0,
        topLevelColumnNames,
        List(topLevelColumnNames.size) { null },
        dimensionsStr,
        hierarchyRoot = columnTreeRoot
    )
}
