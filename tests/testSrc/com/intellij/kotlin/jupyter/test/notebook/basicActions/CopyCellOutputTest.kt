// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.basicActions

import com.intellij.jupyter.core.jupyter.actions.JupyterCopyCellOutputAction
import com.intellij.kotlin.jupyter.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessages
import com.intellij.kotlin.jupyter.test.notebook.execution.ReceivedMessagesTester
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.asSafely
import junit.framework.TestCase
import org.junit.Test
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

class CopyCellOutputTest: KotlinNotebookExecutionBaseTestCase("notebooks/basicActions/copyCellOutput") {
    @Test
    fun testTextPlain() = doTest("This is my output")

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
