package org.jetbrains.kotlinx.jupyter.plugin.lang

import com.intellij.testFramework.ParsingTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class JKTMetaParserTest : ParsingTestCase("", JKTMetaFileType.EXTENSION, JKTMetaParserDefinition()) {
    /**
     * @return path to test data file directory relative to root of this module.
     */
    override fun getTestDataPath(): String {
        return "src/test/testData/parsing"
    }

    override fun skipSpaces() = false
    override fun includeRanges() = true

    @Test
    fun parsing1() {
        doTest(true)
    }
}
