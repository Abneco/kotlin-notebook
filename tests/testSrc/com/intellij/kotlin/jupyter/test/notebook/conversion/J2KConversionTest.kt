// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.conversion

import com.intellij.kotlin.jupyter.test.KotlinNotebookTransformerBaseTestCase
import com.intellij.kotlin.jupyter.test.runners.K1Only
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.TestDataPath
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.kotlin.j2k.copyPaste.ConvertTextJavaCopyPasteProcessor
import org.junit.Assume
import org.junit.Test
import java.awt.datatransfer.StringSelection
import java.io.File

private enum class ConversionType {
    FROM_TEXT_FILE,
    FROM_JAVA_FILE,
}

private class TestData(
    val conversionType: ConversionType,
    val expectedFileMark: String,
)

@K1Only("Investigate why @Throws is not shortened")
@TestDataPath("\$CONTENT_ROOT/testData/notebooks/conversion")
class J2KConversionTest : KotlinNotebookTransformerBaseTestCase() {
    @Test
    fun `testSimpleConversionE FromTextFile`() = doTest(FromTextFile)

    @Test
    fun `testSimpleConversionNL FromTextFile`() = doTest(FromTextFile)

    @Test
    fun `testSimpleConversionE FromJavaFile`() = doTest(FromJavaFile)

    @Test
    fun `testSimpleConversionNL FromJavaFile`() = doTest(FromJavaFile)

    companion object {
        private val FromTextFile = TestData(ConversionType.FROM_TEXT_FILE, "Txt")
        private val FromJavaFile = TestData(ConversionType.FROM_JAVA_FILE, "Java")
    }

    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        val superTestName = super.getTestName(lowercaseFirstLetter)
        return superTestName.substringBefore(' ')
    }

    private fun prepareExpectedCellText(expectedFileMark: String): String {
        val expectedCellFile = getTestFile("${expectedFileMark}.kt.txt")
        Assume.assumeTrue(expectedCellFile.exists())
        return expectedCellFile.readText().prepareText()
    }

    private fun doTest(
        testData: TestData
    ) {
        val expectedCellText = prepareExpectedCellText(testData.expectedFileMark)

        doSimpleTransformerTest(expectedCellText) {
            val javaCode = File(testDataPath).resolve("simpleConversion.txt").readText()

            when (testData.conversionType) {
                ConversionType.FROM_TEXT_FILE -> {
                    CopyPasteManager.getInstance().setContents(StringSelection(javaCode))
                }
                ConversionType.FROM_JAVA_FILE -> {
                    invokeAndWaitIfNeeded {
                        copyContentFromJavaFile(javaCode)
                        myFixture.openFileInEditor(notebookFile.file)
                    }
                }
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
