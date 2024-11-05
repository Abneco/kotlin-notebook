// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.kotlin.jupyter.test.getCells
import com.intellij.kotlin.jupyter.test.isInjectedKtFile
import com.intellij.kotlin.jupyter.test.notebook.execution.KotlinNotebookExecutionBaseTestCase
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.junit.ComparisonFailure


abstract class NotebookQuickFixBaseTest(testDataPath: String) : KotlinNotebookExecutionBaseTestCase("notebooks/codeinsight/quickfix/$testDataPath") {
    override fun getProject(): Project {
        return myFixture.project
    }

    protected var shouldBeAvailableAfterExecution: Boolean = true

    protected fun findActionWithText(name: String, strict: Boolean = false): IntentionAction? {
        repeat(2) {
            for (action in myFixture.availableIntentions) {
                if (if (strict) { name == action.text } else action.text.contains(name)) {
                    return action
                }
            }
        }
        return null
    }


    private fun applyAction(contents: String, fileName: String? = null ) {
        val actionHint = contents.substringAfter("// ").substringBefore("\n").split('\"').filter { it.isNotBlank() && it.isNotEmpty() }
        assert(actionHint.size == 2) { "Action should be in format \"action\" \"true of false\" " }
        val expectedText = actionHint[0]
        val shouldBePresent = actionHint[1].toBoolean()

        val intention = findActionWithText(expectedText)
        if (shouldBePresent) {
            if (intention == null) {
                fail(
                    "Action with text '" + expectedText + "' not found\nAvailable actions:\n" +
                            myFixture.availableIntentions.joinToString(separator = "\n") { "// \"${it.text}\" \"true\"" })
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
                UIUtil.dispatchAllInvocationEvents()

                if (!shouldBeAvailableAfterExecution) {
                    var action = findActionWithText(expectedText)
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

    protected fun doKotlinQuickFixTest(ktFile: PsiFile, documentContent: String) = runInEdtAndWait {
        CommandProcessor.getInstance().executeCommand(project, {
            var fileText = ""
            try {
                fileText = ktFile.text
                TestCase.assertTrue("\"<caret>\" is missing in file \"$ktFile\"", documentContent.contains("<caret>"))

                val contents = StringUtil.convertLineSeparators(fileText)

                applyAction(contents)
            } catch (e: AssertionError) {
                throw e
            } finally {
                ConfigLibraryUtil.unconfigureLibrariesByDirective(myFixture.module, fileText)
            }
        }, "", "")
    }


    protected fun doTest(cellInd: Int? = null) {
        KotlinTestHelpers.registerChooserInterceptor(myFixture.testRootDisposable)

        val notebookFile = configureExecutionTest()
        val injectedFile = getInjectedFile(notebookFile, cellInd) ?: error("No suitable KtFile found in a host")
        val rawContent = FileUtil.loadFile(getTestFile(".ipynb"), true)

        doTestWithJupyterSessionAndBaseDependencies(notebookFile) {
            doKotlinQuickFixTest(injectedFile, rawContent)
        }
    }

    protected fun getInjectedFile(notebookFile: PsiFile, targetCellInd: Int? = null): PsiFile? {
        val cells = notebookFile.getCells()
        val neededCell = if (targetCellInd != null) {
            cells.getOrNull(targetCellInd)!!
        } else {
            error("Invalid cell index provided")
        }

        return ReadAction.compute<PsiFile, Throwable> {
            (InjectedLanguageManager.getInstance(project)
                .getInjectedPsiFiles(neededCell)?.firstOrNull { it.first.containingFile.isInjectedKtFile() }?.first as? PsiFile)
        }
    }

}