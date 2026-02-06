// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.refactorings

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.kotlin.jupyter.test.runners.K1Only
import com.intellij.openapi.editor.CaretState
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.testFramework.TestDataPath
import org.junit.Test

@K1Only("KTNB-819")
@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/refactorings/extractMethod")
class KotlinNotebookExtractMethodTest : RefactoringTestBase("ExtractFunction") {

    @Test
    fun testExtractPair() = doTest { caretModel ->
        val selStart = LogicalPosition(2, 4)
        val selEnd = LogicalPosition(3, 21)
        caretModel.setCaretsAndSelections(listOf(CaretState(selEnd, selStart, selEnd)))
    }

    override fun setUp() {
        super.setUp()
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
    }
}
