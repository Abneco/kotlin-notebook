// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.core.exc.StreamConstraintsException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.intellij.database.datagrid.ArrayBackedNestedTable
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.util.NlsSafe
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
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptions
import org.jetbrains.plugins.notebooks.tables.ColumnTreeNode
import org.jetbrains.plugins.notebooks.tables.DSTableBundle
import org.jetbrains.plugins.notebooks.tables.DSTableData
import org.jetbrains.plugins.notebooks.tables.DSTableDataException
import org.jetbrains.plugins.notebooks.tables.DataId
import org.jetbrains.plugins.notebooks.tables.ExternalTableDataProviderFactory
import org.jetbrains.plugins.notebooks.tables.api.DSDataFrameInfo
import org.jetbrains.plugins.notebooks.tables.api.DSTableCommandExecutor
import org.jetbrains.plugins.notebooks.tables.api.DSTableDataProvider
import org.jetbrains.plugins.notebooks.tables.api.DSTableDataType
import java.util.*
import javax.swing.RowSorter
import javax.swing.SortOrder


internal const val DEFAULT_JSON_MAX_LENGTH = 100000000

class KotlinDataframeTableDataProvider : ExternalTableDataProviderFactory {
    override fun getDataProviderCapableToParseDataOrNull(serializedData: String?): DSTableDataProvider? {
        if (!KotlinNotebookApplicationOptions.get().showDataFrameAsSwing) return null
        if (serializedData == null || !isFormatSupported(serializedData)) return null

        val jsonFactory = JsonFactory()
        jsonFactory.setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxStringLength(Registry.intValue("jupyter.notebook.json.maxStringLength", DEFAULT_JSON_MAX_LENGTH))
                .build()
        )
        val mapper = ObjectMapper(jsonFactory)

        return KotlinDataFrameProvider(mapper)
    }

    private fun isFormatSupported(serializedData: String): Boolean {
        return serializedData.contains(serializedDataframeField) &&
                serializedData.contains(nColsField) &&
                serializedData.contains(nRowsField) &&
                serializedData.contains(columnsField)
    }
}

const val NULL: String = "null"

class KotlinDataFrameProvider(private val mapper: ObjectMapper = ObjectMapper()) : DSTableDataProvider {
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
        }
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

        if (columns.all { it.isBlank() }) return tableVariable

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

    override fun isFallbackToStaticTableSupported(): Boolean = true

    private fun parseFrameInfoFromKotlinDataframeOutput(text: String, isPreview: Boolean): DSDataFrameInfo {
        val rawJson = extractRawJson(text)

        val rows = extractDatasetRows(rawJson)
        if (rows.isEmpty()) {
            val columnNames = rawJson[columnsField].map { it.asText() }.ifEmpty { listOf(" ") }
            return DSDataFrameInfo(
                0,
                0,
                columnNames,
                emptyList(),
                DSTableBundle.message("ds.table.dimensions.info", 0, 0),
                hierarchyRoot = createRoot(columnNames)
            )
        }

        val firstRowJson = mapper.readTree(rows[0])

        val root = extractHierarchy(firstRowJson)
        val columnNames = root.columnChildren.map { it.columnName }

        val nRow = if (isPreview) rows.size else rawJson[nRowsField].asInt()
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

    private fun extractRawJson(text: String): JsonNode {
        val data = mapper.readTree(text)
        return mapper.readTree(data[jsonPayloadField].asText())
    }

    private fun extractDatasetRows(rawJson: JsonNode): List<String> {
        val dataframeNode = rawJson[serializedDataframeField]
        if (dataframeNode.isEmpty) return emptyList()
        val rawRows = asConcatenatedRows(dataframeNode)
        return rawRows.split(separator)
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

    private fun createRoot(childrenNames: List<String>): ColumnTreeNode {
        val root = createRoot()
        for ((index, column) in childrenNames.withIndex()) {
            root.columnChildren.add(ColumnTreeNode(column, index, index, mutableListOf()))
        }

        return root
    }

    private fun extractValues(jsonNode: JsonNode, columns: List<ColumnTreeNode>): List<Any> {
        return columns.map { column ->
            when {
                jsonNode.isValueNode && (column.name == "value" || column.name == "array") -> handleAutogeneratedColumn(jsonNode, column)
                jsonNode.isObject && jsonNode.has(column.name) -> {
                    val value = jsonNode.get(column.name)
                    extractValue(value, column)
                }
                // It is normal to return null here.
                // This situation occurs when there is a mixture of primitives and objects in a nested dataframe.
                // In this case autogenerated columns are created for, but other columns do not have any values.
                else -> NULL
            }
        }
    }

    private fun handleAutogeneratedColumn(jsonNode: JsonNode, column: ColumnTreeNode): Any {
        return if (jsonNode.isArray) deserializeArray(jsonNode) else extractValue(jsonNode, column)
    }

    private fun extractValue(value: JsonNode, column: ColumnTreeNode): Any {
        return when {
            value.isValueNode -> deserializeValue(value)
            value.isObject -> extractValues(value, column.columnChildren)
            value.isArray -> handleArrayValue(value)
            else -> throw IllegalArgumentException("Unsupported JsonNode type encountered when trying to extract value for column: ${column.name} from node: ${value}")
        }
    }

    private fun handleArrayValue(value: JsonNode): Iterable<*> {
        return if (isMultidimensionalArrayOfPrimitives(value)) {
            deserializeArray(value)
        } else {
            val nestedTableHierarchy = extractNestedTableHierarchy(value)
            val nestedRows: Array<Array<Any>> = value.map { arrayNode ->
                extractValues(arrayNode, nestedTableHierarchy.columnChildren).toTypedArray()
            }.toTypedArray()
            ArrayBackedNestedTable(nestedRows, nestedTableHierarchy)
        }
    }


    /**
     *  Determines whether the given [JsonNode] represents a multidimensional array of primitives.
     *  It is safe to check only on the top level because of the way Dataframe serialization works.
     *	It is impossible for objects to be mixed with primitives on any level except the top one.
     */
    private fun isMultidimensionalArrayOfPrimitives(value: JsonNode): Boolean {
        return value.all { !it.isObject }
    }

    private fun extractNestedTableHierarchy(value: JsonNode): ColumnTreeNode {
        return value.find { it.isObject }?.let { extractHierarchy(it) } ?: createRoot()
    }

    private fun deserializeArray(jsonNode: JsonNode): List<*> {
        return jsonNode.map { if (it.isArray) deserializeArray(it) else deserializeValue(it) }
    }

    private fun deserializeValue(jsonNode: JsonNode): Any {
        return when {
            jsonNode.isTextual -> jsonNode.asText()
            jsonNode.isBoolean -> jsonNode.asBoolean()
            jsonNode.isNumber -> {
                when {
                    jsonNode.isIntegralNumber -> {
                        jsonNode.numberValue()
                    }
                    else -> {
                        jsonNode.asDouble()
                    }
                }
            }
            jsonNode.isBinary -> jsonNode.binaryValue()
            jsonNode.isNull -> NULL
            else -> jsonNode.asText()
        }
    }

    private fun parseDataFromKotlinDataframeOutput(id: DataId, text: String): DSTableData {
        val rawJson = extractRawJson(text)

        val rows = extractDatasetRows(rawJson)
        if (rows.isEmpty()) return DSTableData(id, emptyList())

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
