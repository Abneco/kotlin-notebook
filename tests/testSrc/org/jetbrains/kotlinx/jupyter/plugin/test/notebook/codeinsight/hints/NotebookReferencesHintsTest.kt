// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.codeinsight.hints

import org.jetbrains.kotlinx.jupyter.plugin.codeinsight.KotlinNotebookReferencesTypeHintsProvider
import org.junit.Ignore
import org.junit.Test

class NotebookReferencesHintsTest : AbstractNotebookTypeHintsBaseTest() {
    override fun getTestDataPath() = "${super.getTestDataPath()}/references"
    override val provider = KotlinNotebookReferencesTypeHintsProvider()

    @Test
    @Ignore("Correct path to type definition differs on TC")
    fun testProperties() {
        doTest(provider, 0) {
            it.propertyType = true
        }
    }
}