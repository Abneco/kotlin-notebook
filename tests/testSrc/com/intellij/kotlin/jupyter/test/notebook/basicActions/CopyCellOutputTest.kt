// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.basicActions

import com.intellij.jupyter.core.jupyter.actions.JupyterCopyCellOutputAction
import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.testFramework.TestDataPath
import org.junit.Test

@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/basicActions/copyCellOutput")
class CopyCellOutputTest: KotlinNotebookTestCase() {

    @Test
    fun textPlain() = runNotebookTest {
        executeCell(0)
        performEditorAction(JupyterCopyCellOutputAction::class.simpleName!!)
        assertClipboardContent("This is my output")
    }

    @Test
    fun stream() = runNotebookTest {
        executeCell(0)
        performEditorAction(JupyterCopyCellOutputAction::class.simpleName!!)
        assertClipboardContent("printing 123")
    }
}
