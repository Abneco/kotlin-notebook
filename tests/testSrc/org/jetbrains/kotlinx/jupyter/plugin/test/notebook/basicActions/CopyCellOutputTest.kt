// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.basicActions

import com.intellij.idea.IJIgnore
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.asSafely
import junit.framework.TestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessages
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.ReceivedMessagesTester
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterCopyCellOutputAction
import org.junit.Test
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

class CopyCellOutputTest: KotlinNotebookExecutionBaseTestCase() {

    override fun getTestDataPath() = "$baseTestDataPath/notebooks/basicActions/copyCellOutput"

    @Test
    fun testTextPlain() = doTest("This is my output")

    @IJIgnore(issue = "KTNB-499")
    @Test
    fun testStream() = doTest("printing 123")

    fun doTest(expectedBufferContents: String) {
        doTestAfterExecution(object: ReceivedMessagesTester {
            override val expectedCellsCount: Int
                get() = 1

            override fun assertCellMessages(cellNum: Int, messages: ReceivedMessages) {}
        }) {
            runInEdtAndWait {
                myFixture.performEditorAction(JupyterCopyCellOutputAction::class.simpleName!!)
                val actualContents: StringSelection = CopyPasteManager.getInstance().contents.asSafely<StringSelection>()
                    ?: error("Expected buffer to contain string selection, but it doesn't")
                TestCase.assertEquals(expectedBufferContents, actualContents.getTransferData(DataFlavor.stringFlavor))
            }
        }
    }
}
