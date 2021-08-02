// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.test.metaLanguage

import com.intellij.testFramework.ParsingTestCase
import org.jetbrains.kotlinx.jupyter.plugin.lang.JKTMetaFileType
import org.jetbrains.kotlinx.jupyter.plugin.lang.grammar.JKTMetaParserDefinition
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath

class JKTMetaParserTest : ParsingTestCase("", JKTMetaFileType.EXTENSION, JKTMetaParserDefinition()) {
    /**
     * @return path to test data file directory relative to root of this module.
     */
    override fun getTestDataPath() = "$baseTestDataPath/metaLanguage/parsing"

    override fun skipSpaces() = false
    override fun includeRanges() = true

    fun testSimpleParsing() {
        doTest(true)
    }
}
