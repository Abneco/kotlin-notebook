// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.tables

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.lang.Language
import com.jetbrains.python.debugger.pydev.TableCommandType
import com.jetbrains.python.debugger.pydev.tables.CommandOutputType
import org.jetbrains.kotlin.idea.KotlinLanguage
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
    override fun isTableDataFormatSupported(text: DSTableText): Boolean = KotlinDataframeParsing.isKotlinDataFrame(text)

    override fun isTableDataFormatSupported(messageContentData: ObjectNode): Boolean =
        KotlinDataframeParsing.isKotlinDataFrame(messageContentData)

    override fun getDataProvider(): DSTableDataProvider = KotlinDataFrameProvider()

    override fun extractSerializedData(messageContentData: ObjectNode): String = KotlinDataframeParsing.extractSerializedDataFrame(messageContentData)

    override fun isLanguageSupported(lang: Language): Boolean = lang == KotlinLanguage.INSTANCE
}

class KotlinDataFrameProvider : DSTableDataProvider {
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

    fun getSliceCommand(initCommand: String, isInteractive: Boolean, start: Int, end: Int): String {
        return if (isInteractive) {
            """
            DISPLAY(RepresentationChangeWrapper(when ($initCommand) {
              is Pivot<*> -> ($initCommand as Pivot<*>).frames().toDataFrame().filter { it.index() >= ${start} && it.index() < ${end} }
              is ReducedPivot<*> -> ($initCommand as ReducedPivot<*>).values().toDataFrame().filter { it.index() >= ${start} && it.index() < ${end} }
              is PivotGroupBy<*> -> ($initCommand as PivotGroupBy<*>).frames().filter { it.index() >= ${start} && it.index() < ${end} }
              is ReducedPivotGroupBy<*> -> ($initCommand as ReducedPivotGroupBy<*>).values().filter { it.index() >= ${start} && it.index() < ${end} }
              is SplitWithTransform<*, *, *> -> ($initCommand as SplitWithTransform<*, *, *>).into().filter { it.index() >= ${start} && it.index() < ${end} }
              is Merge<*, *, *> -> ($initCommand as Merge<*, *, *>).into("merged").filter { it.index() >= ${start} && it.index() < ${end} }
              is Gather<*, *, *, *> -> ($initCommand as Gather<*, *, *, *>).into("key", "value").filter { it.index() >= ${start} && it.index() < ${end} }
              is Update<*, *> -> ($initCommand as Update<*, *>).df.filter { it.index() >= ${start} && it.index() < ${end} }
              is Convert<*, *> -> ($initCommand as Convert<*, *>).df.filter { it.index() >= ${start} && it.index() < ${end} }
              is FormattedFrame<*> -> ($initCommand as FormattedFrame<*>).df.filter { it.index() >= ${start} && it.index() < ${end} }
              is AnyCol -> (dataFrameOf($initCommand as AnyCol)).filter { it.index() >= ${start} && it.index() < ${end} }
              is AnyRow -> (($initCommand as AnyRow).toDataFrame()).filter { it.index() >= ${start} && it.index() < ${end} }
              is GroupBy<*, *> -> (($initCommand as GroupBy<*, *>).toDataFrame()).filter { it.index() >= ${start} && it.index() < ${end} }
              else -> ($initCommand as DataFrame<*>).filter { it.index() >= ${start} && it.index() < ${end} }
            }), "")
            """.trimIndent()
        } else {
            initCommand
        }
    }

    override fun getSortingCommand(initCommand: String, sortKeys: List<RowSorter.SortKey>, columns: List<String>): String {
        return getSortCommand(initCommand, sortKeys, columns)
    }

    private fun getSortCommand(initExpression: String, sortKeys: List<RowSorter.SortKey>, cols: List<String>?): String {
        if (cols.isNullOrEmpty()) return initExpression

        val kotlinDataframeSortKeys = sortKeys
            .map { "\"${cols[it.column]}\"${if (it.sortOrder == SortOrder.DESCENDING) ".desc()" else ""}" }
            .toMutableList()

        if (sortKeys.size == 1 && sortKeys[0].sortOrder != SortOrder.DESCENDING) {
            kotlinDataframeSortKeys.add(kotlinDataframeSortKeys[0])
        }

        return "(($initExpression as DataFrame<*>).sortBy { ${kotlinDataframeSortKeys.joinToString(" and ")} })"
    }

    override fun isFallbackToTruncatedSupported(): Boolean = true

    private fun parseFrameInfoFromKotlinDataframeOutput(text: String): DSDataFrameInfo {
        val mapper = ObjectMapper()

        val rawJson = mapper.readTree(text)

        val nRow = rawJson["nrow"].asInt()
        val nCol = rawJson["ncol"].asInt()
        val columnNames = mutableListOf<String>()
        (rawJson["columns"] as ArrayNode).elements().forEach {
            columnNames.add(it.asText())
        }
        val dimensionsStr = DSTableBundle.message("ds.table.dimensions.info", nRow, nCol)

        return DSDataFrameInfo(nRow, 0, columnNames, dimensionsStr)
    }

    private fun parseDataFromKotlinDataframeOutput(id: DataId, text: String): DSTableData {
        val mapper = ObjectMapper()

        val rawJson = mapper.readTree(text)

        val rawRows = asConcatenatedRows(rawJson["kotlin_dataframe"])
        val rows = rawRows.split("kotlin_dataframe_sep")
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
            (text as ArrayNode).joinToString(separator = "kotlin_dataframe_sep")
        } else {
            text.asText()
        }
    }
}
