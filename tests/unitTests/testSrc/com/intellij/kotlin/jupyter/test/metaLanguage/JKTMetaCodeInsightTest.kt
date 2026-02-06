// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.metaLanguage

import com.intellij.kotlin.jupyter.test.baseTestDataPathWithHome
import com.intellij.kotlin.jupyter.test.runners.KotlinNotebookTestRunner
import com.intellij.kotlin.jupyter.test.runners.ListenableTest
import com.intellij.kotlin.jupyter.test.runners.ListenableTestImpl
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(KotlinNotebookTestRunner::class)
class JKTMetaCodeInsightTest :
    LightJavaCodeInsightFixtureTestCase(),
    ListenableTest by ListenableTestImpl()
{
    /**
     * @return path to test data file directory relative to root of this module.
     */
    override fun getTestDataPath() = "$baseTestDataPathWithHome/metaLanguage/codeInsight"

    @Test
    fun `test magics completion`() {
        myFixture.testCompletionVariants("completion1.juktm", "use", "useLatestDescriptors")
    }


    @Test
    fun `test simple annotator`() {
        myFixture.configureByFile("annotator1.juktm")
        myFixture.checkHighlighting(false, true, false)
    }

    @Test
    fun `test versions completion`() {
        myFixture.configureByFile("completion2.juktm")
        val variants = myFixture.completeBasic().toList()
        // Check that the first published version is the last (bottom-most) completion variant
        assertEquals("0.4.0-dev-16", variants.last().lookupString)
        // Check that library parameters are placed first
        assertTrue('.' !in variants.first().lookupString)
    }

    override fun setUp() {
        wrapSetUp(this) { super.setUp() }
    }

    override fun tearDown() {
        wrapTearDown(this) { super.tearDown() }
    }
}
