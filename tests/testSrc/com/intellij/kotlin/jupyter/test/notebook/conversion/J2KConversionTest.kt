// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.conversion

import com.intellij.kotlin.jupyter.test.KotlinNotebookTransformerBaseTestCase
import com.intellij.kotlin.jupyter.test.runners.K1Only
import com.intellij.kotlin.jupyter.test.runners.PluginModeAwareParametersRunnerFactory
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.TestDataPath
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.kotlin.j2k.copyPaste.ConvertTextJavaCopyPasteProcessor
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.datatransfer.StringSelection
import java.io.File

class MarkedTestParameter<T : Any>(
    val value: T,
    val expectedFileMark: String,
    private val testNameMark: String,
) {
    override fun toString() = testNameMark
}

@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(PluginModeAwareParametersRunnerFactory::class)
@K1Only("Investigate why @Throws is not shortened")
@TestDataPath("\$CONTENT_ROOT/testData/notebooks/conversion")
class J2KConversionTest(
    private val fromJavaFile: MarkedTestParameter<Boolean>,
) : KotlinNotebookTransformerBaseTestCase() {
    @Test
    fun testSimpleConversionE() = doTest()

    @Test
    fun testSimpleConversionNL() = doTest()

    companion object {
        @Parameterized.Parameters(name = "{index}. Parameters: <{0}>, <{1}>")
        @JvmStatic
        fun `data`(): List<Array<Any>> {
            return listOf(
                arrayOf(MarkedTestParameter(false, "Txt", "from text file")),
                arrayOf(MarkedTestParameter(true, "Java", "from Java file")),
            )
        }
    }

    private fun prepareExpectedCellText(): String {
        val expectedCellFile = getTestFile("${fromJavaFile.expectedFileMark}.kt.txt")
        Assume.assumeTrue(expectedCellFile.exists())
        return expectedCellFile.readText().prepareText()
    }

    private fun doTest() {
        val expectedCellText = prepareExpectedCellText()

        doSimpleTransformerTest(expectedCellText) {
            val javaCode = File(testDataPath).resolve("simpleConversion.txt").readText()

            if (fromJavaFile.value) {
                invokeAndWaitIfNeeded {
                    copyContentFromJavaFile(javaCode)
                    myFixture.openFileInEditor(notebookFile.file)
                }
            } else {
                CopyPasteManager.getInstance().setContents(StringSelection(javaCode))
            }

            KotlinEditorOptions.getInstance().isDonTShowConversionDialog = true
            ConvertTextJavaCopyPasteProcessor.Util.conversionPerformed = false

            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
            myFixture.performEditorAction(IdeActions.ACTION_PASTE)
        }
    }

    @RequiresEdt
    private fun copyContentFromJavaFile(javaFile: String) {
        val javaPsi = myFixture.addFileToProject("MyJavaFile.java", javaFile)
        myFixture.openFileInEditor(javaPsi.virtualFile)
        myFixture.performEditorAction(IdeActions.ACTION_SELECT_ALL)
        myFixture.performEditorAction(IdeActions.ACTION_COPY)
    }


    private fun String.prepareText() = lines().joinToString("\n") { it.trimEnd() }
}
