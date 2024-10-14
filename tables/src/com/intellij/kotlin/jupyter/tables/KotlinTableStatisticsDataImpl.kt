// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.tables

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.intellij.jupyter.core.jackson
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.scientific.tables.api.ColumnDescriptionStatistics
import com.intellij.scientific.tables.api.DSTableCommandExecutor
import com.intellij.scientific.tables.api.DescriptionStatisticsValue
import com.intellij.scientific.tables.api.TableStatisticsData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
private class CommandRunner(val coroutineScope: CoroutineScope)

/**
 * Class responsible for creating a [TableStatisticsData] from a Kotlin DataFrame.
 */
class KotlinTableStatisticsDataImpl(
    private val commandExecutor: DSTableCommandExecutor,
    private val tableVariable: String,
) : TableStatisticsData {
    private val tableStatisticsDataDeferred = CompletableDeferred<List<ColumnDescriptionStatistics>?>()
    private val requested = AtomicBoolean(false)

    override suspend fun await() {
        tableStatisticsDataDeferred.await()
    }

    override fun request() {
        if (!requested.compareAndSet(false, true)) return
        service<CommandRunner>().coroutineScope.launch(Dispatchers.IO) {
            // Evaluate and parse output of `df.describe()`
            // We extract as JSON as it makes it easier to manipulate it into the relevant
            // data structures.
            val describeJson = commandExecutor.executeCommand("""
               import org.jetbrains.kotlinx.dataframe.jupyter.KotlinNotebookPluginUtils
               // Use println() to force output to STREAM rather than DISPLAY
               val value = $tableVariable 
               println(if (value != null) KotlinNotebookPluginUtils.convertToDataFrame(value).describe().toJson() else "")
            """.trimIndent())
            val columnStats = extractDescribeData(describeJson)
            tableStatisticsDataDeferred.complete(columnStats)

            // Add support for `df[columnName].valuesCount()` here.
            // Some questions to figure out:
            //  - We should probably ignore columns with unique values
            //  - Should we ignore NA values or not?
        }.invokeOnCompletion {
            tableStatisticsDataDeferred.cancel("cannot get description", it)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val tableStatisticsData: List<ColumnDescriptionStatistics>?
        get() {
            return if (tableStatisticsDataDeferred.isCompleted && tableStatisticsDataDeferred.getCompletionExceptionOrNull() == null) {
                tableStatisticsDataDeferred.getCompleted()
            } else {
                null
            }
        }

    private fun extractDescribeData(descriptionAsJson: String): List<ColumnDescriptionStatistics>? {
        return try {
            val statisticsArray = jackson.readTree(descriptionAsJson)
            if (statisticsArray !is ArrayNode) return null

            // Parse each column for its stats. Each column has the following format:
            //{
            //    "name": "survived",
            //    "type": "Int",
            //    "count": 891,
            //    "unique": 2,
            //    "nulls": 0,
            //    "top": "0",
            //    "freq": 549,
            //    "mean": 0.3838383838383838,
            //    "std": 0.4865924542648585,
            //    "min": "0",
            //    "median": "0",
            //    "max": "1"
            //},
            val columnDescriptionStatistics = mutableListOf<ColumnDescriptionStatistics>()
            for (columnStats: JsonNode in statisticsArray) {
                val stats = mutableListOf<DescriptionStatisticsValue>()
                columnStats.fieldNames()
                    .asSequence()
                    .toList()
                    .map { fieldName ->
                        // Currently, values are passed through as-is. Consider if we should format
                        // some types so they become more readable, .e.g., restrict number of decimals
                        // in doubles/floats.
                        stats.add(DescriptionStatisticsValue(fieldName, columnStats[fieldName].asText()))
                    }
                if (stats.isNotEmpty()) {
                    columnDescriptionStatistics.add(ColumnDescriptionStatistics(stats))
                }
            }
            return columnDescriptionStatistics.ifEmpty { null }
        } catch (ex: Throwable) {
            when(ex) {
                is JsonMappingException -> null
                is JsonProcessingException -> null
                else -> error(ex)
            }
        }
    }
}
