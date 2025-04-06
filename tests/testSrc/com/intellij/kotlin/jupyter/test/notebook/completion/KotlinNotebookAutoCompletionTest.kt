// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.kotlin.jupyter.core.util.findPsiFile
import com.intellij.kotlin.jupyter.test.KotlinNotebookBaseTestCase
import com.intellij.kotlin.jupyter.test.LookupFinishMode
import com.intellij.kotlin.jupyter.test.currentKotlinPluginMode
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.Ignore
import org.junit.Test

@TestDataPath("\$CONTENT_ROOT/testData/notebooks/autocompletion")
class KotlinNotebookAutoCompletionTest : KotlinNotebookBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile

    @Test
    fun testCommandCompletion() = doTest { tester ->
        val elements = tester.typeAndGetLookup("l")
        elements.shouldBeNull()
        assertActualTextContains(":help")
    }

    @Test
    fun testMagicCompletion() = doTest { tester ->
        val elements = tester.typeAndGetLookup("s")
        elements.asLookupStrings() shouldBe listOf("use", "useLatestDescriptors")
    }

    @Test
    fun testKotlinCompletionInSameCell() = doTest { tester ->
        val elements = tester.typeAndGetLookup(".")
        elements.asLookupStrings() shouldContainAll listOf("displays", "lastCell", "kernelVersion")
    }

    @Test
    fun testKotlinCompletionInsertionCorrectStd() = doTest { tester ->
        tester.typeAndFinishLookup("lis", LookupFinishMode.TAB) {
            it.lookupString == "listOf"
        }
        val expectedText = when(currentKotlinPluginMode) {
            KotlinPluginMode.K1 -> "listOf(x)"
            KotlinPluginMode.K2 -> "listOf<>()"
        }
        assertActualTextContains(expectedText)
    }

    @Test
    @Ignore("KTNB-843")
    fun testKotlinCompletionInsertionCorrectReplace() = doTest { tester ->
        tester.typeAndFinishLookup("i", LookupFinishMode.TAB) {
            it.lookupString == "id"
        }
        assertActualTextContains("id(x)")
    }

    @Test
    fun testKotlinCompletionInsertionCorrectAdd() = doTest { tester ->
        tester.typeAndFinishLookup("i", LookupFinishMode.ENTER) {
            it.lookupString == "id"
        }
        assertActualTextContains("id()listOf(x)")
    }

    @Test
    fun testKotlinCompletionOverrideMethod() = doTest { tester ->
        tester.typeAndFinishLookup("de", LookupFinishMode.ENTER) {
            it.allLookupStrings.contains("hashCode")
        }
        assertActualText("""
            class Clazz {
                override fun hashCode(): Int {
                    return super.hashCode()
                }
            }
            
            
        """.trimIndent())
    }

    @Test
    fun testKotlinCompletionInAnotherCell() = doTest { tester ->
        tester.typeAndGetLookup(".").shouldBeEmpty()
    }
    
    private fun List<LookupElement>?.asLookupStrings() = this?.map { it.lookupString }.orEmpty()

    private fun doTest(action: (CompletionAutoPopupTester) -> Unit) {
        val notebookFile = configureByJupyterFile()
        val psiFile = invokeAndWaitIfNeeded {
            myFixture.editor.setMode(NotebookEditorMode.EDIT)
            notebookFile.file.findPsiFile(project)!!
        }

        doTestWithJupyterSessionAndBaseDependencies(psiFile) {
            val completionTester = CompletionAutoPopupTester(myFixture)
            completionTester.runWithAutoPopupEnabled {
                action(completionTester)
            }
        }
    }

    override fun runInDispatchThread(): Boolean {
        return false
    }
}
