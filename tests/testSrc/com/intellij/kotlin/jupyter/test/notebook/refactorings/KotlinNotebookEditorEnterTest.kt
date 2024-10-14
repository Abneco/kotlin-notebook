// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.refactorings

import com.intellij.kotlin.jupyter.test.baseTestDataPath
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.LogicalPosition
import org.junit.Test

class KotlinNotebookEditorEnterTest : RefactoringTestBase("EditorEnter") {

    override fun getTestDataPath() = "$baseTestDataPath/notebooks/refactorings/editorEnter"

    @Test
    fun testEnterInLambda() = doTest { caretModel ->
        caretModel.setCaretsAndSelections(listOf(CaretState(LogicalPosition(1, 19), null, null)))
    }
}
