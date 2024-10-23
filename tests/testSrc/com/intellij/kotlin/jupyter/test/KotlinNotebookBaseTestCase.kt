// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Disposer.newDisposable
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.util.concurrency.ThreadingAssertions
import io.kotest.common.runBlocking
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.plugins.notebooks.tests.JupyterBaseTestCase
import org.jetbrains.plugins.notebooks.tests.JupyterCommonRule
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
abstract class KotlinNotebookBaseTestCase(private val dataPath: String) : JupyterBaseTestCase(), ExpectedPluginModeProvider {
    protected open val notebookFile: BackedNotebookVirtualFile
        get() = when (val file = myFixture.kotlinNotebookFile) {
            is BackedNotebookVirtualFile -> file
            else -> error("Null notebook file found for ${myFixture.file.virtualFile}")
        }

    override fun getBasePath(): @NonNls String {
        return "$baseTestDataPath/$dataPath"
    }

    protected fun setUpDependenciesSynchronously(cellsToExecute: List<Int> = emptyList()) {
        val testCaseDisposable = newDisposable(testRootDisposable, "setUpScriptingDependencies")
        val updater = TestNotebookScriptsDependenciesUpdater(project, notebookFile, testCaseDisposable)
        runBlocking {
            updater.setUpDependenciesSynchronously(cellsToExecute)
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
        get() = KotlinPluginMode.K1

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
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
            val firstLookupElement = myFixture?.lookupElements?.firstOrNull(filter)
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