package org.jetbrains.kotlinx.jupyter.plugin.lang

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class JKTMetaCodeInsightTest : LightJavaCodeInsightFixtureTestCase() {
    /**
     * @return path to test data file directory relative to root of this module.
     */
    override fun getTestDataPath() = "src/test/testData/codeInsight"

    @Test
    fun completion1() {
        myFixture.testCompletionVariants("completion1.juktm", "use", "useLatestDescriptors")
    }

    @Test
    fun annotator1() {
        myFixture.configureByFile("annotator1.juktm")
        myFixture.checkHighlighting(false, true, false)
    }
}
