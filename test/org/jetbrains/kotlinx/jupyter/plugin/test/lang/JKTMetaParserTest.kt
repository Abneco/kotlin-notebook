package org.jetbrains.kotlinx.jupyter.plugin.test.lang

import com.intellij.testFramework.ParsingTestCase
import org.jetbrains.kotlinx.jupyter.plugin.lang.JKTMetaFileType
import org.jetbrains.kotlinx.jupyter.plugin.lang.grammar.JKTMetaParserDefinition
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath

class JKTMetaParserTest : ParsingTestCase("", JKTMetaFileType.EXTENSION, JKTMetaParserDefinition()) {
    /**
     * @return path to test data file directory relative to root of this module.
     */
    override fun getTestDataPath(): String {
        return "$baseTestDataPath/parsing"
    }

    override fun skipSpaces() = false
    override fun includeRanges() = true

    fun testSimpleParsing() {
        doTest(true)
    }
}
