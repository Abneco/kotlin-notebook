// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.outputs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.asSafely
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParserFormatV2
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeParsing
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.tables.KotlinDataframeTableDataProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookApplicationOptions
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.plugins.notebooks.tables.DataId
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File


@RunWith(JUnit4::class)
class KotlinDataframeTableDataProviderTest : BasePlatformTestCase() {
    @Test
    fun `test format check`() {
        val (_, data) = prepareProviderAndData()
        Assert.assertTrue(KotlinDataframeParsing.isKotlinDataFrame(data))
    }

    @Test
    fun `test serialized content extraction`() {
        val (_, data) = prepareProviderAndData()

        val mapper = ObjectMapper()
        val serializedDf = data.toString()

        val messageContent = mapper.readTree(serializedDf)["application/kotlindataframe+json"]
        val rawJson = mapper.readTree(messageContent.asText())

        val nRow = rawJson["nrow"].asInt()
        val nCol = rawJson["ncol"].asInt()

        Assert.assertEquals(nRow, 20)
        Assert.assertEquals(nCol, 14)
        val columnNames = mutableListOf<String>()
        (rawJson["columns"] as ArrayNode).elements().forEach {
            columnNames.add(it.asText())
        }

        Assert.assertEquals(columnNames, actualColumns)

        Assert.assertTrue(rawJson.has("kotlin_dataframe"))
    }

    @Test
    fun `test DSDataFrameInfo extraction`() {
        val (dataframeProvider, data) = prepareProviderAndData()
        KotlinNotebookApplicationOptions.get().showDataFrameAsSwing = true
        val provider = dataframeProvider.getDataProviderCapableToParseDataOrNull(data.toString())

        Assert.assertNotNull(provider!!)

        val frameInfo = provider.parseTextToFrameInfo(data.toString())

        Assert.assertEquals(frameInfo.rows, 20)
        Assert.assertEquals(frameInfo.columnNames, actualColumns)
        Assert.assertEquals(frameInfo.dim, "${frameInfo.rows} rows × ${frameInfo.columnNames.size} columns")
    }

    @Test
    fun `test DSTableData extraction`() {
        val (dataframeProvider, data) = prepareProviderAndData()
        KotlinNotebookApplicationOptions.get().showDataFrameAsSwing = true
        val provider = dataframeProvider.getDataProviderCapableToParseDataOrNull(data.toString())

        Assert.assertNotNull(provider!!)

        val tableData = provider.parseTextToTableData(DataId(19), data.toString())

        Assert.assertEquals(tableData.cols!!.size, 14)

        val firstRow = tableData.cols!!.map { it[0] }

        Assert.assertEquals(
            firstRow,
            listOf(
                1,
                1,
                "Allen, Miss. Elisabeth Walton",
                "null",
                29.0,
                "null",
                "null",
                "24160",
                211.3375,
                "B5",
                "null",
                "2",
                "null",
                "St Louis, MO"
            )
        )
    }

    @Test
    fun `test formatV2 parsing`() {
        val parser = KotlinDataframeParserFormatV2(ObjectMapper())
        val data = readData("dataframe_formatv2.json").toString()

        val frameInfo = parser.parseDataFrameInfo(data)
        val frameData = parser.parseDataFrameData(data)

        Assert.assertEquals(frameInfo.rowsNum, 10)
        Assert.assertEquals(frameInfo.topLevelColumnNames.size, 11)

        val firstRow = frameData.map { it[0] }

        Assert.assertEquals(
            firstRow,
            listOf(
                "0",
                "00:20",
                "11°C",
                "Mostly cloudy.",
                "17 km/h",
                "94%",
                "1011 mbar",
                "5km",
                "2012",
                "1",
                "1"
            )
        )
    }

    @Test
    fun `test DSTableData extraction format v2`() {
        val (dataframeProvider, data) = prepareProviderAndDataFormatV2()
        KotlinNotebookApplicationOptions.get().showDataFrameAsSwing = true
        val provider = dataframeProvider.getDataProviderCapableToParseDataOrNull(data.toString())

        Assert.assertNotNull(provider!!)

        val tableData = provider.parseTextToTableData(DataId(19), data.toString())

        Assert.assertEquals(tableData.cols!!.size, 11)
    }

    private fun prepareProviderAndData(): Pair<KotlinDataframeTableDataProvider, ObjectNode> {
        return Pair(KotlinDataframeTableDataProvider(), readData("dataframe.json"))
    }

    private fun prepareProviderAndDataFormatV2(): Pair<KotlinDataframeTableDataProvider, ObjectNode> {
        return Pair(KotlinDataframeTableDataProvider(), readData("dataframe_formatv2.json"))
    }

    private fun readData(fileName: String): ObjectNode {
        val jupyterDataframeResponseFile = File("$baseTestDataPath/outputs/$fileName")
        return ObjectMapper().readTree(jupyterDataframeResponseFile).asSafely<ObjectNode>()
            ?: throw RuntimeException("${jupyterDataframeResponseFile.path} not found")
    }
}


private val actualColumns = listOf(
    "pclass",
    "survived",
    "name",
    "sex",
    "age",
    "sibsp",
    "parch",
    "ticket",
    "fare",
    "cabin",
    "embarked",
    "boat",
    "body",
    "homedest"
)
