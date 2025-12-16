// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.server.JupyterServers
import com.intellij.kotlin.jupyter.test.runners.KotlinNotebookTestRunner
import com.intellij.kotlin.jupyter.test.runners.ListenableTest
import com.intellij.kotlin.jupyter.test.runners.ListenableTestImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Disposer.newDisposable
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.notebooks.tests.JupyterBaseTestCase
import org.jetbrains.plugins.notebooks.tests.JupyterCommonRule
import org.jetbrains.plugins.notebooks.tests.configureByJupyterFile
import org.junit.Rule
import org.junit.runner.RunWith
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.name
import kotlin.io.path.pathString

private const val CONTENT_ROOT_VARIABLE: @NonNls String = $$"$CONTENT_ROOT"
private const val CONTENT_ROOT: @NonNls String = "/plugins/kotlin/jupyter/tests"
private const val PROJECT_ROOT_VARIABLE: @NonNls String = $$"$PROJECT_ROOT"
private const val PROJECT_ROOT: @NonNls String = ""

// TODO Migrate this class KotlinNotebookTestCase
@RunWith(KotlinNotebookTestRunner::class)
abstract class KotlinNotebookBaseTestCase :
    JupyterBaseTestCase(),
    ExpectedPluginModeProvider,
    ListenableTest by ListenableTestImpl()
{
    protected open val notebookFile: BackedNotebookVirtualFile
        get() = when (val file = myFixture.kotlinNotebookFile) {
            is BackedNotebookVirtualFile -> file
            else -> error("Null notebook file found for ${myFixture.file?.virtualFile}")
        }

    override fun getBasePath(): @NonNls String {
        val testDataPath = this::class.java.getAnnotation(TestDataPath::class.java)?.value
        val testMetadataPath = this::class.java.getAnnotation(TestMetadata::class.java)?.value
        return FileUtilRt
            .toSystemIndependentName(
                listOfNotNull(testDataPath, testMetadataPath).joinToString(FileSystems.getDefault().separator)
            )
            .replace(CONTENT_ROOT_VARIABLE, CONTENT_ROOT)
            .replace(PROJECT_ROOT_VARIABLE, PROJECT_ROOT)
    }

    protected fun getTestFile(): Path {
        // we're using TestCase.getName() to get the function name, should be safe since the test name isn't customized anywhere
        val testMetadata = this::class.java.getMethod(name).getAnnotation(TestMetadata::class.java)
        return Path.of(testDataPath, testMetadata?.value ?: "${getTestName(true)}.ipynb")
    }

    protected fun configureByJupyterFile(
        fileEditorProvider: FileEditorProvider? = null
    ): BackedNotebookVirtualFile = invokeAndWaitIfNeeded {
        val testFile = getTestFile()
        myFixture.configureByJupyterFile(
            jupyterFileName = testFile.name,
            testDataPath = testFile.parent.absolute().pathString,
            fileEditorProvider = fileEditorProvider,
        )
    }

    protected fun doTestWithJupyterSessionAndBaseDependencies(psiFile: PsiFile, action: () -> Unit) {
        runWithJupyterSession(psiFile) {
            setUpDependenciesSynchronously(emptyList())
            action()
        }
    }

    protected fun setUpProjectSdk(sdk: Sdk = IdeaTestUtil.getMockJdk18()) {
        invokeAndWaitIfNeeded {
            WriteAction.run<Throwable> {
                val registeredJdk = ProjectJdkTable.getInstance().findJdk(sdk.name)
                if (registeredJdk == null) {
                    ProjectJdkTable.getInstance().addJdk(sdk, myFixture.projectDisposable)

                    ProjectRootManager.getInstance(project).projectSdk = sdk
                }
            }
        }
    }

    protected fun setUpProjectSdkIfNeeded() {
        setUpProjectSdk()
    }

    /**
     * Waits until all dependencies are set up.
     * This method should not be called on [EDT] as it will cause a deadlock.
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
        wrapSetUp(this) {
            setUpWithKotlinPlugin { super.setUp() }
            Disposer.register(testRootDisposable, JupyterServers.getInstance())
        }
    }

    override fun tearDown() {
        wrapTearDown(this@KotlinNotebookBaseTestCase) {
            super.tearDown()
        }
    }

    fun getTestFile(suffix: String): Path {
        return Path.of(testDataPath, "${getTestName(true)}$suffix")
    }
}
