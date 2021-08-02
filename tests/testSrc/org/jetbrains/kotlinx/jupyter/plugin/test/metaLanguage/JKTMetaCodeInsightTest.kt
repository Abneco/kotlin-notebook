// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.test.metaLanguage

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath

class JKTMetaCodeInsightTest : LightJavaCodeInsightFixtureTestCase() {
    /**
     * @return path to test data file directory relative to root of this module.
     */
    override fun getTestDataPath() = "$baseTestDataPath/metaLanguage/codeInsight"

    fun `test magics completion`() {
        myFixture.testCompletionVariants("completion1.juktm", "use", "useLatestDescriptors")
    }


    fun `test simple annotator`() {
        myFixture.configureByFile("annotator1.juktm")
        myFixture.checkHighlighting(false, true, false)
    }
}
