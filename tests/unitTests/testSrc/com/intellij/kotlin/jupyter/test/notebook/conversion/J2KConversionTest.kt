// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.conversion

import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.TestDataPath
import com.intellij.util.concurrency.annotations.RequiresEdt
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.kotlin.j2k.copyPaste.ConvertTextJavaCopyPasteProcessor
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test
import java.awt.datatransfer.StringSelection
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

private enum class ConversionType {
    FROM_TEXT_FILE,
    FROM_JAVA_FILE,
}

private class TestData(
    val conversionType: ConversionType,
    val expectedFileMark: String,
)

@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/conversion")
class J2KConversionTest : KotlinNotebookTestCase() {

    @Test
    @TestMetadata("simpleConversionE.ipynb")
    fun `testSimpleConversionE FromTextFile`() = runTest(FromTextFile)

    @Test
    @TestMetadata("simpleConversionNL.ipynb")
    fun `testSimpleConversionNL FromTextFile`() = runTest(FromTextFile)

    @Test
    @TestMetadata("simpleConversionE.ipynb")
    fun `testSimpleConversionE FromJavaFile`() = runTest(FromJavaFile)

    @Test
    @TestMetadata("simpleConversionNL.ipynb")
    fun `testSimpleConversionNL FromJavaFile`() = runTest(FromJavaFile)

    private fun prepareExpectedCellText(expectedFileMark: String): String {
        val testFile = getTestFile()
        val strippedTestFileName = testFile.name.removeSuffix(".ipynb")
        val expectedCellFile = testFile.parent.resolve("$strippedTestFileName$expectedFileMark.kt.txt")
        if (!expectedCellFile.exists()) error("Missing expected file: ${expectedCellFile.toAbsolutePath()}")
        return expectedCellFile.readText().prepareText()
    }

    private fun runTest(testData: TestData) {
        val expectedCellText = prepareExpectedCellText(testData.expectedFileMark)
        val javaCode = Path(testDataPath).resolve("simpleConversion.txt").readText()
        runNotebookTest {
            // Prepare file content to copy into the Notebook
            when (testData.conversionType) {
                ConversionType.FROM_TEXT_FILE -> {
                    CopyPasteManager.getInstance().setContents(StringSelection(javaCode))
                }
                ConversionType.FROM_JAVA_FILE -> {
                    invokeAndWaitIfNeeded {
                        copyContentFromJavaFile(javaCode)
                        myFixture.openFileInEditor(notebookFile.virtualFile)
                    }
                }
            }
            // Copy content into the current cell
            KotlinEditorOptions.getInstance().isDonTShowConversionDialog = true
            ConvertTextJavaCopyPasteProcessor.Util.conversionPerformed = false
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
            myFixture.performEditorAction(IdeActions.ACTION_PASTE)

            currentCellContent.trimEnd() shouldBe expectedCellText.trimEnd()
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

    companion object {
        private val FromTextFile = TestData(ConversionType.FROM_TEXT_FILE, "Txt")
        private val FromJavaFile = TestData(ConversionType.FROM_JAVA_FILE, "Java")
    }
}
