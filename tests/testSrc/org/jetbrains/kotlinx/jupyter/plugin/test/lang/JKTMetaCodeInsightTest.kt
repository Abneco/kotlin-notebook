package org.jetbrains.kotlinx.jupyter.plugin.test.lang

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath

class JKTMetaCodeInsightTest : LightJavaCodeInsightFixtureTestCase() {
    /**
     * @return path to test data file directory relative to root of this module.
     */
    override fun getTestDataPath() = "$baseTestDataPath/codeInsight"

    fun `test simple completion`() {
        myFixture.testCompletionVariants("completion1.juktm", "use", "useLatestDescriptors")
    }


    fun `test simple annotator`() {
        myFixture.configureByFile("annotator1.juktm")
        myFixture.checkHighlighting(false, true, false)
    }
}
