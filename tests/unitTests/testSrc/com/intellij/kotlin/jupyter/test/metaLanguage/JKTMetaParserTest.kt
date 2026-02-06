// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.metaLanguage

import com.intellij.kotlin.jupyter.core.language.meta.JKTMetaFileType
import com.intellij.kotlin.jupyter.core.language.meta.grammar.JKTMetaParserDefinition
import com.intellij.kotlin.jupyter.test.baseTestDataPathWithHome
import com.intellij.kotlin.jupyter.test.runners.KotlinNotebookTestRunner
import com.intellij.kotlin.jupyter.test.runners.ListenableTest
import com.intellij.kotlin.jupyter.test.runners.ListenableTestImpl
import com.intellij.testFramework.ParsingTestCase
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(KotlinNotebookTestRunner::class)
class JKTMetaParserTest :
    ParsingTestCase("", JKTMetaFileType.EXTENSION, JKTMetaParserDefinition()),
    ListenableTest by ListenableTestImpl()
{
    /**
     * @return path to test data file directory relative to root of this module.
     */
    override fun getTestDataPath() = "$baseTestDataPathWithHome/metaLanguage/parsing"

    override fun skipSpaces() = false
    override fun includeRanges() = true

    @Test
    fun testSimpleParsing() {
        doTest(true)
    }

    @Test
    fun testCommandsParsing() {
        doTest(true)
    }

    override fun setUp() {
        wrapSetUp(this) { super.setUp() }
    }

    override fun tearDown() {
        wrapTearDown(this) { super.tearDown() }
    }
}
