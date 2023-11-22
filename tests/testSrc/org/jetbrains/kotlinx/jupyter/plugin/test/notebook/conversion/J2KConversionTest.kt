// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.conversion

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vfs.VirtualFile
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.conversion.copy.ConvertTextJavaCopyPasteProcessor
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.kotlinx.jupyter.plugin.test.KotlinNotebookBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.kotlinx.jupyter.plugin.test.cartesianProduct
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.setMode
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
) : KotlinNotebookBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile

    override fun getTestDataPath() = "$baseTestDataPath/notebooks/conversion"

    @Test
    fun testSimpleConversion() = doTest()

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
        val notebookFile = myFixture.configureByJupyterFile(templateFileName.value, testDataPath)

        val javaCode = File(testDataPath).resolve("${myTestName()}.txt").readText()

        if (fromJavaFile.value) {
            val javaPsi = myFixture.addFileToProject("MyJavaFile.java", javaCode)
            myFixture.openFileInEditor(javaPsi.virtualFile)
            myFixture.performEditorAction(IdeActions.ACTION_SELECT_ALL)
            myFixture.performEditorAction(IdeActions.ACTION_COPY)
            myFixture.openFileInEditor(notebookFile.file)
        } else {
            CopyPasteManager.getInstance().setContents(StringSelection(javaCode))
        }

        myFixture.setCaresAboutInjection(true)
        invokeAndWaitIfNeeded {
            setMode(NotebookEditorMode.EDIT)
        }
        originalVirtualFile = myFixture.file.virtualFile

        KotlinEditorOptions.getInstance().isDonTShowConversionDialog = true
        ConvertTextJavaCopyPasteProcessor.conversionPerformed = false

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
        myFixture.performEditorAction(IdeActions.ACTION_PASTE)

        val document = myFixture.editor.document
        val actualText = runReadAction {
            document.text
        }.prepareText()
        TestCase.assertEquals(expectedCellText, actualText)
    }

    private fun String.prepareText() = lines().joinToString("\n") { it.trimEnd() }
}
