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
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.setMode
import org.junit.Test
import java.awt.datatransfer.StringSelection
import java.io.File

private const val emptyCellTemplate = "template.ipynb"
private const val newLineCellTemplate = "templateNewLine.ipynb"

private val testNameRegex = Regex("""(.*[^0-9])([0-9]*)""")

class J2KConversionTest : KotlinNotebookBaseTestCase() {
    override lateinit var originalVirtualFile: VirtualFile

    override fun getTestDataPath() = "$baseTestDataPath/notebooks/conversion"

    @Test
    fun testSimpleConversion() = doTest(emptyCellTemplate)

    @Test
    fun testSimpleConversion2() = doTest(newLineCellTemplate)

    private fun myTestName(): String {
        return getTestName(true)
    }

    private fun rawTestName(): String {
        val myTestName = myTestName()
        val match = testNameRegex.find(myTestName) ?: return myTestName
        val rawPart = match.groupValues[1]
        return rawPart
    }

    private fun doTest(templateFileName: String = emptyCellTemplate) {
        myFixture.configureByJupyterFile(templateFileName, testDataPath)
        myFixture.setCaresAboutInjection(true)
        invokeAndWaitIfNeeded {
            setMode(NotebookEditorMode.EDIT)
        }
        originalVirtualFile = myFixture.file.virtualFile

        KotlinEditorOptions.getInstance().isDonTShowConversionDialog = true
        ConvertTextJavaCopyPasteProcessor.conversionPerformed = false

        fun String.prepareText() = lines().joinToString("\n") { it.trimEnd() }

        val javaCode = File(testDataPath).resolve("${rawTestName()}.txt").readText()
        val expectedCellText = File(testDataPath).resolve("${myTestName()}.kt.txt").readText().prepareText()

        CopyPasteManager.getInstance().setContents(StringSelection(javaCode))

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
        myFixture.performEditorAction(IdeActions.ACTION_PASTE)

        val document = myFixture.editor.document
        val actualText = runReadAction {
            document.text
        }.prepareText()
        TestCase.assertEquals(expectedCellText, actualText)
    }
}
