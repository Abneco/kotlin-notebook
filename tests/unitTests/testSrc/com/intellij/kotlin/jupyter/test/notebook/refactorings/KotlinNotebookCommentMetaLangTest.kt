// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.refactorings

import com.intellij.idea.IJIgnore
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.testFramework.TestDataPath
import org.junit.Test

@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/refactorings/commentMetaLang")
class KotlinNotebookCommentMetaLangTest : RefactoringTestBase(IdeActions.ACTION_COMMENT_LINE) {

    @IJIgnore(issue = "KTNB-1376")
    @Test
    fun testLineCommentUse() = doTest { caretModel ->
        caretModel.setCaretsAndSelections(listOf(CaretState(LogicalPosition(1, 4), null, null)))
    }
}
