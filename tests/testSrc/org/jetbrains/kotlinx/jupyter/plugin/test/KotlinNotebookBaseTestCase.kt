// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.listeners.NotebookCodeSnippetsChangeListener
import org.jetbrains.kotlinx.jupyter.plugin.test.notebook.completion.finishLookup
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.configureByJupyterFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.tests.JupyterBaseTestCase
import org.jetbrains.plugins.notebooks.tests.JupyterCommonRule
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.setMode
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.util.concurrent.CountDownLatch

@RunWith(JUnit4::class)
abstract class KotlinNotebookBaseTestCase : JupyterBaseTestCase(), ExpectedPluginModeProvider{
    protected val setupBarrier = CountDownLatch(1)

    override lateinit var originalVirtualFile: VirtualFile

    protected lateinit var jupyterSession: JupyterNotebookSession

    protected lateinit var injectionFixture: InjectionTestFixture

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
        injectionFixture = InjectionTestFixture(myFixture)
        myFixture.project.messageBus.connect(project).subscribe(
            NotebookCodeSnippetsChangeListener.TOPIC,
            object : NotebookCodeSnippetsChangeListener {
                override fun scriptsClassesChanged(file: BackedNotebookVirtualFile) {
                    if (::jupyterSession.isInitialized && jupyterSession.virtualFile == file) {
                        setupBarrier.countDown()
                    }
                }
        })
    }

    override fun tearDown() {
        try {
            if (::jupyterSession.isInitialized) {
                jupyterSession.deleteSession()
            }
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    protected open fun configureTestDependencies(
        copyNotebookToProject: Boolean = false,
        caresAboutInjection: Boolean = true,
        fileEditorProvider: FileEditorProvider? = null
    ): PsiFile {
        TestLoggerFactory.enableDebugLogging(myFixture.projectDisposable, javaClass)

        if (caresAboutInjection) {
            myFixture.setCaresAboutInjection(true)

            // If something is executed before highlighting is invoked,
            // it may trigger daemon restarting later asynchronously
            (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
        }

        myFixture.configureByJupyterFile(
            jupyterFileName = "${getTestName(true)}.ipynb",
            testDataPath = testDataPath,
            isCopyToProject = copyNotebookToProject,
            fileEditorProvider = fileEditorProvider
        )
        invokeAndWaitIfNeeded {
            myFixture.editor.setMode(NotebookEditorMode.EDIT)
        }
        originalVirtualFile = myFixture.file.virtualFile // `myFixture.file` may return the file which is injected inside one of the cells
        val notebookFile = runReadAction {
            FileContextUtil.getFileContext(myFixture.file)?.containingFile ?: myFixture.file
        }

        initSession(notebookFile)

        setUpScriptingDependencies(myFixture)

        return notebookFile
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
            lookup.finishLookup(mode, firstLookupElement)
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

    private fun initSession(notebookFile: PsiFile) {
        try {
            jupyterSession = initJupyterSession(notebookFile)
            setupBarrier.await()
        } catch (ex: Exception) {
            LOG.warn("Exception during setup of jupyter session: ", ex)
        }
    }
}