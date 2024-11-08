// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.conversion

import com.intellij.kotlin.jupyter.test.KotlinNotebookTransformerBaseTestCase
import com.intellij.kotlin.jupyter.test.cartesianProduct
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.kotlin.idea.conversion.copy.ConvertTextJavaCopyPasteProcessor
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.plugins.notebooks.tests.configureByJupyterFile
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.datatransfer.StringSelection
import java.io.File

private const val emptyCellTemplate = "template.ipynb"
private const val newLineCellTemplate = "templateNewLine.ipynb"

class MarkedTestParameter<T: Any>(
    val value: T,
    val expectedFileMark: String,
    private val testNameMark: String,
) {
    override fun toString() = testNameMark
}

@RunWith(Parameterized::class)
class J2KConversionTest(
    private val templateFileName: MarkedTestParameter<String>,
    private val fromJavaFile: MarkedTestParameter<Boolean>,
) : KotlinNotebookTransformerBaseTestCase("notebooks/conversion") {
    @Test
    fun testSimpleConversion() = doTest()

    override fun runInDispatchThread(): Boolean {
        return false
    }

    companion object {
        @Parameterized.Parameters(name = "{index}. Parameters: <{0}>, <{1}>")
        @JvmStatic
        fun `data`(): List<Array<Any>> {
            return cartesianProduct(
                listOf(
                    MarkedTestParameter(emptyCellTemplate, "E", "to the empty cell"),
                    MarkedTestParameter(newLineCellTemplate, "Nl", "to the cell with newline only")
                ),
                listOf(
                    MarkedTestParameter(false, "Txt", "from text file"),
                    MarkedTestParameter(true, "Java", "from Java file")
                )
            )
        }
    }


    private fun myTestName(): String {
        return getTestName(true)
    }

    private fun prepareExpectedCellText(): String {
        val expectedFileSuffix = listOf(templateFileName, fromJavaFile).joinToString("") { it.expectedFileMark }
        val expectedCellFile = File(testDataPath).resolve("${myTestName()}$expectedFileSuffix.kt.txt")
        Assume.assumeTrue(expectedCellFile.exists())
        return expectedCellFile.readText().prepareText()
    }

    private fun doTest() {
        val expectedCellText = prepareExpectedCellText()

        doSimpleTransformerTest(
            expectedCellText,
            notebookFactory = { myFixture.configureByJupyterFile(templateFileName.value, testDataPath) }
        ) {
            val javaCode = File(testDataPath).resolve("${myTestName()}.txt").readText()

            if (fromJavaFile.value) {
                invokeAndWaitIfNeeded {
                    copyContentFromJavaFile(javaCode)
                }
            } else {
                CopyPasteManager.getInstance().setContents(StringSelection(javaCode))
            }

            KotlinEditorOptions.getInstance().isDonTShowConversionDialog = true
            ConvertTextJavaCopyPasteProcessor.conversionPerformed = false

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
