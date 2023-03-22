// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.tables

import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Utils for parsing encoded table data produced by Kotlin Dataframe library
 */
object KotlinDataframeParsing {
    const val jsonPayloadField = "application/kotlindataframe+json"
    const val serializedDataframeField = "kotlin_dataframe"
    const val separator = "kotlin_dataframe_sep"
    const val columnsField = "columns"
    const val nRowsField = "nrow"
    const val nColsField = "ncol"


    fun isKotlinDataFrame(dataObject: ObjectNode): Boolean {
        if (!dataObject.has(jsonPayloadField)) return false
        val jsonPayload = dataObject[jsonPayloadField].asText() ?: return false

        return jsonPayload.contains(serializedDataframeField)
    }
}
