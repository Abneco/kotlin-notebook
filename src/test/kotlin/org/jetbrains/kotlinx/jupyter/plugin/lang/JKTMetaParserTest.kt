package org.jetbrains.kotlinx.jupyter.plugin.lang

import com.intellij.testFramework.ParsingTestCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class JKTMetaParserTest : ParsingTestCase("", JKTMetaFileType.EXTENSION, JKTMetaParserDefinition()) {

    private lateinit var testInfo: TestInfo

    @BeforeEach
    fun setup(testInfo: TestInfo) {
        this.testInfo = testInfo
        setUp()
    }

    @Test
    fun parsing1() {
        doTest(true)
    }

    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        return getTestName(testInfo.testMethod.get().name, lowercaseFirstLetter)
    }

    /**
     * @return path to test data file directory relative to root of this module.
     */
    override fun getTestDataPath(): String {
        return "src/test/testData"
    }

    override fun skipSpaces() = false
    override fun includeRanges() = true
}
