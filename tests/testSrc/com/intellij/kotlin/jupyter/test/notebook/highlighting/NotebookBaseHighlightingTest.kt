// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.highlighting

import com.intellij.kotlin.jupyter.core.util.getElementTextRangeInHost
import com.intellij.kotlin.jupyter.test.HighlightCheckStrategy
import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.kotlin.jupyter.test.runners.K2Only
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.TestDataPath
import io.kotest.matchers.collections.shouldContain
import org.jetbrains.kotlin.psi.KtElement
import org.junit.Ignore
import org.junit.Test

@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/highlighting")
class NotebookBaseHighlightingTest: KotlinNotebookTestCase() {

    @Test
    fun simpleNotebook() = runNotebookTest {
        runHighlighting().assertHighlightResult(HighlightCheckStrategy.OnlyValidSyntax)
    }

    @Test
    fun correctHighlightingWithMarkdown() = runNotebookTest {
        runHighlighting().assertHighlightResult(HighlightCheckStrategy.OnlyValidSyntax)
    }

    @K2Only("This fails on K1 for unknown reasons")
    @Test
    fun withShadowedUnresolved() = runNotebookTest {
        runHighlighting().assertHighlightResult(HighlightCheckStrategy.ShadowedErrors)
    }

    @Test
    @K2Only("JDK setup is special for K2 mode")
    @Ignore("KTNB-1119")
    fun jdkTest() = runNotebookTest {
        runHighlighting().assertHighlightResult(HighlightCheckStrategy.OnlyValidSyntax)
    }

    @K2Only("This fails on K1 for unknown reasons")
    @Test
    fun resolvedAfterExecution() = runNotebookTest {
        runHighlighting().assertHighlightResult(HighlightCheckStrategy.ShadowedErrors)
        assertEquals(2, cellCount)
        executeCell(0, waitForDependencies = true)
        moveCaretToCell(1)
        runHighlighting().assertHighlightResult(HighlightCheckStrategy.OnlyValidSyntax)
    }

    @Test
    fun serializationHighlighting() = runNotebookTest {
        executeCell(0, waitForDependencies = true)
        runHighlighting().result.let { result ->
            assertNotEmpty(result)
            val importantInfos = result.filter { it.severity > HighlightSeverity.INFORMATION }
            assertEmpty(importantInfos)
        }
    }

    /**
     * Note: this test does not check the correctness of visual placement of the fix hint
     */
    @K2Only("This fails on K1 for unknown reasons")
    @Test
    fun importFixRangeAlignedWithElement() = runNotebookTest {
        val elementTextRangeInHost = readAction {
            val elementUnderCaret = elementUnderCaret.parent as KtElement

            elementUnderCaret.getElementTextRangeInHost()
        }

        val importFixes = findQuickFixes { descriptor, _ ->
            "Import" in descriptor.action.text
        }
        val ranges = importFixes.map { it.fixRange }
        ranges shouldContain elementTextRangeInHost
    }
}
