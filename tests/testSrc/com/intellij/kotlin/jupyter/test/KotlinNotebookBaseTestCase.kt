// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.server.JupyterServers
import com.intellij.kotlin.jupyter.test.runners.KotlinPluginAwareRunner
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Disposer.newDisposable
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import io.kotest.common.runBlocking
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.plugins.notebooks.tests.JupyterBaseTestCase
import org.jetbrains.plugins.notebooks.tests.JupyterCommonRule
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File

@RunWith(KotlinPluginAwareRunner::class)
abstract class KotlinNotebookBaseTestCase(private val dataPath: String) : JupyterBaseTestCase(), ExpectedPluginModeProvider {
    protected open val notebookFile: BackedNotebookVirtualFile
        get() = when (val file = myFixture.kotlinNotebookFile) {
            is BackedNotebookVirtualFile -> file
            else -> error("Null notebook file found for ${myFixture.file?.virtualFile}")
        }

    override fun getBasePath(): @NonNls String {
        return "$baseTestDataPath/$dataPath"
    }

    protected fun doTestWithJupyterSessionAndBaseDependencies(psiFile: PsiFile,  action: () -> Unit) {
        runWithJupyterSession(psiFile) {
            setUpDependenciesSynchronously(emptyList())
            action()
        }
    }

    /**
     * Waits until all dependencies are set up.
     * This method should not be called on EDT] as it will cause a deadlock.
     */
    @RequiresBackgroundThread
    protected fun setUpDependenciesSynchronously(
        cellsToExecute: List<Int> = emptyList(),
        updateMode: ScriptingUpdateMode = ScriptingUpdateMode.NotebookFileFocused
    ) {
        val testCaseDisposable = newDisposable(testRootDisposable, "setUpScriptingDependencies")
        val cellEstimation = 1 + cellsToExecute.size
        val fileOrNull = if (updateMode == ScriptingUpdateMode.NotebookFileFocused) {
            notebookFile
        } else null
        val updater = TestNotebookScriptsDependenciesUpdater(project, fileOrNull, cellEstimation, testCaseDisposable)
        runBlocking {
            updater.setUpDependenciesSynchronously(myFixture)
        }
        waitForReadyIndexes(myFixture)

        Disposer.dispose(testCaseDisposable)
    }

    @JvmField
    @Rule
    val kotlinNotebookCommonRule = JupyterCommonRule(
        withClearPasswordSafe = false,
        withProductionDataManagerRule = false,
        withClearJupyterSettings = true
    )

    override val pluginMode: KotlinPluginMode
        get() = currentKotlinPluginMode

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
        Disposer.register(testRootDisposable, JupyterServers.getInstance())
    }

    fun getTestFile(suffix: String): File {
        return File(testDataPath, "${getTestName(true)}$suffix")
    }

    protected fun CompletionAutoPopupTester.typeAndFinishLookup(
        string: String,
        mode: LookupFinishMode = LookupFinishMode.ENTER,
        filter: (LookupElement) -> Boolean
    ) {
        ThreadingAssertions.assertBackgroundThread()
        typeWithPauses(string)
        joinCommit()
        invokeAndWaitIfNeeded {
            val elements = myFixture.completeBasic()
            val firstLookupElement = elements.firstOrNull(filter)
            lookup.finishLookup(mode.completionChar, firstLookupElement)
        }
        joinCommit()
    }


    protected fun assertActualText(expectedText: String) {
        assertEquals(expectedText, actualText())
    }

    protected fun assertActualTextContains(expectedText: String) {
        val actualText = actualText()
        assertTrue("<$actualText> should contain <$expectedText>", actualText.contains(expectedText))
    }

    private fun actualText() = runReadAction { myFixture.editor.document.text }
}