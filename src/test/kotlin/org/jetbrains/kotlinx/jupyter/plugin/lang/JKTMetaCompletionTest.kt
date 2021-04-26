package org.jetbrains.kotlinx.jupyter.plugin.lang

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class JKTMetaCompletionTest : LightJavaCodeInsightFixtureTestCase() {
    /**
     * @return path to test data file directory relative to root of this module.
     */
    override fun getTestDataPath() = "src/test/testData/completion"

    @Test
    fun completion1() {
        myFixture.testCompletionVariants("testData1.juktm", "use", "useLatestDescriptors")
    }
}
