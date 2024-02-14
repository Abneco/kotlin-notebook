// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.llm.util

import org.jetbrains.plugins.notebooks.tables.api.DSDataFrameInfo

internal fun DSDataFrameInfo.createDataFrameChatAttachmentText(tableVariable: String): String {
    val tableInfo = this
    val columnData = tableInfo.columnNames.map { it.ifEmpty { "column without name" } }
        .zip(tableInfo.columnTypes).map { "${it.first} of type ${it.second}" }

    val describeInfo =  tableInfo.dataDescription?.columnDescriptionData
    val fullColumnData: String =  columnData.joinToString("\n")
    if (describeInfo != null) {
        columnData.zip(
            describeInfo.map { it.columnStatistics.joinToString { statistics -> "${statistics.statisticsName}: ${statistics.statisticsValue}" } })
            .joinToString("\n") { "${it.first}. ${it.second}" }
    }
    else {
        columnData.joinToString("\n")
    }

    val tableType = tableInfo.tableType?.typeDataWithDocumentation

    return "`${tableVariable}` points to Kotlin DataFrame of type: ${tableType?.frameworkName ?: ""}.${tableType?.shortName ?: ""} with " +
            "dimensions ${tableInfo.dim}.\n" +
            "Please make a succinct description of the data and highlight the most important things.\n" +
            "Use ${tableVariable} variable name for all code snippets for working with this DataFrame.\n" +
            "Do not suggest using `describe()`, point out that column statistics is available in table header instead.\n" +
            "DataFrame has the following columns:\n${fullColumnData}"
}