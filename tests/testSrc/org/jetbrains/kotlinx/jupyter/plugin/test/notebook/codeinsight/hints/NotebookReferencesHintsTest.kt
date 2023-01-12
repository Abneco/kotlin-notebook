// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.codeinsight.hints

import org.jetbrains.kotlinx.jupyter.plugin.codeinsight.KotlinNotebookReferencesTypeHintsProvider
import org.junit.Test

class NotebookReferencesHintsTest : AbstractNotebookTypeHintsBaseTest() {
    override fun getTestDataPath() = "${super.getTestDataPath()}/references"
    override val provider = KotlinNotebookReferencesTypeHintsProvider()
    override val markerShift: Int
        get() = super.markerShift //- 1

    @Test
    fun testProperties() {
        doTest(provider, 0) {
            it.propertyType = true
        }
    }
}