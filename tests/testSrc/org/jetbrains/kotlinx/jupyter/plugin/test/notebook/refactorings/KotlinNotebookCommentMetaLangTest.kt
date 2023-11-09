// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.refactorings

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.LogicalPosition
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.junit.Test

class KotlinNotebookCommentMetaLangTest: RefactoringTestBase(IdeActions.ACTION_COMMENT_LINE) {

    override fun getTestDataPath() = "$baseTestDataPath/notebooks/refactorings/commentMetaLang"

    @Test
    fun testLineCommentUse() = doTest { caretModel ->
        caretModel.setCaretsAndSelections(listOf(CaretState(LogicalPosition(1, 4), null, null)))
    }
}
