// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.completion.finishLookup
import org.jetbrains.plugins.notebooks.tests.JupyterBaseTestCase
import org.jetbrains.plugins.notebooks.tests.JupyterCommonRule
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
abstract class KotlinNotebookBaseTestCase : JupyterBaseTestCase() {
    @JvmField
    @Rule
    val kotlinNotebookCommonRule = JupyterCommonRule(
      withClearPasswordSafe = false,
      withProductionDataManagerRule = false,
      withClearJupyterSettings = true
    )

    fun getTestFile(suffix: String): File {
        return File(testDataPath, "${getTestName(true)}$suffix")
    }

    protected fun CompletionAutoPopupTester.typeAndFinishLookup(
        string: String,
        mode: LookupFinishMode = LookupFinishMode.ENTER,
        filter: (LookupElement) -> Boolean
    ) {
        typeWithPauses(string)
        finishLookupForElement(mode, filter)
    }

    private fun CompletionAutoPopupTester.finishLookupForElement(
        mode: LookupFinishMode = LookupFinishMode.ENTER,
        filter: (LookupElement) -> Boolean
    ) {
        invokeAndWaitIfNeeded {
            val firstLookupElement = myFixture?.lookupElements?.firstOrNull(filter)
            lookup.finishLookup(mode, firstLookupElement)
        }
        joinCommit()
    }


    protected fun assertActualText(expectedText: String) {
      assertEquals(expectedText, actualText())
    }

    protected fun assertActualTextContains(expectedText: String) {
        val actualText = actualText()
        assertTrue("<$actualText> should contain <$expectedText>", actualText.contains(expectedText))
    }

    private fun actualText() = runReadAction { myFixture.editor.document.text }
}