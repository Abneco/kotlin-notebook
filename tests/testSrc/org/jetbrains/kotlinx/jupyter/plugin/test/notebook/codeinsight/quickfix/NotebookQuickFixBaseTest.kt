// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook.codeinsight.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlinx.jupyter.plugin.test.baseTestDataPath
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import org.jetbrains.kotlinx.jupyter.plugin.util.getInjectedKtFiles
import org.junit.ComparisonFailure


abstract class NotebookQuickFixBaseTest : KotlinNotebookExecutionBaseTestCase() {
    override fun getTestDataPath() = "$baseTestDataPath/notebooks/codeinsight/quickfix"

    override fun getBasePath(): String {
        return testDataPath
    }

    override fun getProject(): Project {
        return myFixture.project
    }

    protected var shouldBeAvailableAfterExecution: Boolean = true

    protected fun findActionWithText(intentions: Iterable<IntentionAction>?, name: String, strict: Boolean = false): IntentionAction? {
        for (action in (intentions ?: myFixture.availableIntentions)) {
            if (if (strict) { name == action.text } else action.text.startsWith(name)) {
                return action
            }
        }
        return null
    }


    private fun applyAction(topLevelEditor: Editor, topLevelFile: PsiFile, contents: String, fileName: String? = null ) {
        val actionHint = contents.substringAfter("// ").substringBefore("\n").split('\"').filter { it.isNotBlank() && it.isNotEmpty() }
        assert(actionHint.size == 2) { "Action should be in format \"action\" \"true or false\" " }
        val expectedText = actionHint[0]
        val shouldBePresent = actionHint[1].toBoolean()
        val intentions = CodeInsightTestFixtureImpl.getAvailableIntentions(topLevelEditor, topLevelFile)

        val intention = findActionWithText(intentions, expectedText)
        if (shouldBePresent) {
            if (intention == null) {
                fail(
                    "Action with text '" + expectedText + "' not found\nAvailable actions:\n" +
                            intentions.joinToString(separator = "\n") { "// \"${it.text}\" \"true\"" })
                return
            }

            val applyQuickFix = true
            val stubComparisonFailure: ComparisonFailure?
            if (applyQuickFix) {
                stubComparisonFailure = try {
                    myFixture.launchAction(intention)
                    null
                } catch (invocationFailure: ComparisonFailure) {
                    invocationFailure
                }

                UIUtil.dispatchAllInvocationEvents()
                NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

                if (!shouldBeAvailableAfterExecution) {
                    var action = findActionWithText(null, expectedText)
                    action = if (action == null) null else IntentionActionDelegate.unwrap(action)
                    assertNull(
                        "Action '${expectedText}' (${action?.javaClass}) is still available after its invocation in test " + fileName,
                        action
                    )
                }

            } else {
                stubComparisonFailure = null
            }
            val expectedResult = FileUtil.loadFile(getTestFile(".kt.expected"), true)
            stubComparisonFailure?.let { throw it }

            TestCase.assertEquals(expectedResult, myFixture.file.text)
        } else {
            assertNull("Action with text ${expectedText} is present, but should not", intention)
        }
    }

    protected fun doKotlinQuickFixTest(topLevelEditor: Editor, topLevelFile: PsiFile, ktFile: PsiFile, documentContent: String) = runInEdtAndWait {
        var fileText = ""
        try {
            fileText = ktFile.text
            TestCase.assertTrue("\"<caret>\" is missing in file \"$ktFile\"",
                                documentContent.indexOf(CodeInsightTestFixture.CARET_MARKER) > 0
            )

            val contents = StringUtil.convertLineSeparators(fileText)

            applyAction(topLevelEditor, topLevelFile, contents)
        } catch (e: AssertionError) {
            throw e
        } finally {
            ConfigLibraryUtil.unconfigureLibrariesByDirective(myFixture.module, fileText)
        }
    }


    protected fun doTest(cellInd: Int? = null) {
        val notebookFile = configureTestDependencies(caresAboutInjection = true)
        val injectedFile = ReadAction.compute<PsiFile, Throwable> {
            (if (cellInd != null) notebookFile.getInjectedKtFiles().getOrNull(cellInd) else null)
                ?: error("Invalid cell index provided")
        } ?: error("No suitable KtFile found in a host")
        val rawContent = FileUtil.loadFile(getTestFile(".ipynb"), true)
        val topLevelEditor = runReadAction { injectionFixture.topLevelEditor }

        runInEdtAndWait {
            myFixture.doHighlighting()
        }
        CodeInsightTestFixtureImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(notebookFile, topLevelEditor)

        doKotlinQuickFixTest(topLevelEditor, notebookFile, injectedFile, rawContent)
    }

}