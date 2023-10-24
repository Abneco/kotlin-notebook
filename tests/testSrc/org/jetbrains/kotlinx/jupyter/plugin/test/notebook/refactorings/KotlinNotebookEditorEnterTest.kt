// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.refactorings

import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.LogicalPosition
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.junit.Test

class KotlinNotebookEditorEnterTest : RefactoringTestBase("EditorEnter") {

    override fun getTestDataPath() = "$baseTestDataPath/notebooks/refactorings/editorEnter"

    @Test
    fun testEnterInLambda() = doTest { caretModel ->
        caretModel.setCaretsAndSelections(listOf(CaretState(LogicalPosition(1, 19), null, null)))
    }
}
