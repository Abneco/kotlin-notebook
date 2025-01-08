// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.server.JupyterServers
import com.intellij.kotlin.jupyter.core.logging.KotlinNotebookLoggerFactory
import com.intellij.kotlin.jupyter.core.settings.sessionRunMode
import com.intellij.kotlin.jupyter.test.runners.TestContext
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Disposer.newDisposable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.forEachGuaranteed
import org.jetbrains.annotations.NonNls
import org.jetbrains.jupyter.builder.NotebookBuilder
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.notebooks.tests.JupyterBaseTestCase
import org.jetbrains.plugins.notebooks.tests.configureByJupyterFile
import org.jetbrains.plugins.notebooks.tests.withSwingMarkdownRenderMode
import java.io.File

/**
 * Base class for all notebook tests. The entry point is the [runNotebookTest] method which provides
 * access to an API wrapper making it possible to write tests in a more human-readable way.
 */
abstract class KotlinNotebookTestCase: JupyterBaseTestCase(), ExpectedPluginModeProvider {

    // We cannot run on the EDT thread as Kernel Execution also runs there, which can result in deadlocks
    // when waiting for kernel status messages.
    override fun runInDispatchThread(): Boolean = false

    companion object {
        private const val CONTENT_ROOT_VARIABLE: @NonNls String = "\$CONTENT_ROOT"
        private const val CONTENT_ROOT: @NonNls String = "/plugins/kotlin/jupyter/tests"
        private const val PROJECT_ROOT_VARIABLE: @NonNls String = "\$PROJECT_ROOT"
        private const val PROJECT_ROOT: @NonNls String = ""
    }

    private var notebookRunner: NotebookTestBuilder? = null
    override lateinit var originalVirtualFile: VirtualFile

    override val pluginMode: KotlinPluginMode
        get() {
            val vmValue = System.getProperty("idea.kotlin.plugin.use.k2") ?: return KotlinPluginMode.K1
            return when (vmValue) {
                "true" -> KotlinPluginMode.K2
                else -> KotlinPluginMode.K1
            }
        }

    private val backedNotebookFile: BackedNotebookVirtualFile
        get() = when (val file = myFixture.kotlinNotebookFile) {
            is BackedNotebookVirtualFile -> file
            else -> error("Null notebook file found for ${myFixture.file?.virtualFile}")
        }

    override fun setUp() {
        super.setUp()
        KotlinNotebookLoggerFactory.enableUnitTestMode()
    }

    override fun tearDown() {
        // We attempt to run as much teardown logic as possible.
        // This is mostly done in an attempt to close any threads or thread pools,
        // as they will trigger an assertion in com.intellij.testFramework.common.ThreadLeakTracker.
        try {
            listOf(
                { resetAndValidateLoggedErrors() },
                { notebookRunner?.tearDown() },
            ).forEachGuaranteed { it() }
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            listOf(
                { super.tearDown() },
                // Uncomment for testing project leak. See KTNB-527.
                // { TestApplicationManager.testProjectLeak() }
            ).forEachGuaranteed { it() }
        }
    }

    private fun resetAndValidateLoggedErrors() {
        val testLogs = KotlinNotebookLoggerFactory.getTrackedLogs()
        KotlinNotebookLoggerFactory.disableUnitTestMode()

        val fatalErrorsFound = testLogs.filter { it.level == LogLevel.ERROR }
        val fatalWarningsFound =
            testLogs.filter {
                it.level == LogLevel.WARNING &&
                it.message?.contains("has been compiled by a more recent version of the Java Runtime") == true
            }

        if (fatalErrorsFound.isNotEmpty() || fatalWarningsFound.isNotEmpty()) {
            val logsToReport = fatalErrorsFound + fatalWarningsFound
            val errorMessage = buildString {
                appendLine("Unexpected logs was found during the test (${logsToReport.size}):")
                testLogs.forEach {
                    appendLine("------------------------------------------------------------")
                    appendLine("[${it.level}] ${it.message}")
                    it.t?.let { exception -> appendLine(exception.stackTraceToString()) }
                }
                appendLine("------------------------------------------------------------")
            }
            fail(errorMessage)
        }
    }

    override fun getBasePath(): @NonNls String {
        val testDataPath = this::class.java.getAnnotation(TestDataPath::class.java)?.value
        val testMetadataPath = this::class.java.getAnnotation(TestMetadata::class.java)?.value
        return FileUtil.toSystemIndependentName(listOfNotNull(testDataPath, testMetadataPath).joinToString(File.separator))
            .replace(CONTENT_ROOT_VARIABLE, CONTENT_ROOT)
            .replace(PROJECT_ROOT_VARIABLE, PROJECT_ROOT)
    }

    /**
     * Creates a temporary notebook file using [notebookFile] builder, then runs the [test] with this file loaded into the test editor.
     * The file is deleted on JVM exit.
     *
     * @see [com.intellij.testFramework.fixtures.CodeInsightTestFixture.file].
     */
    fun runNotebookTest(
        notebookFile: NotebookBuilder.() -> Unit,
        setupScriptDependencies: Boolean = true,
        test: NotebookTestBuilder.() -> Unit,
    ) {
        runNotebookTestInternal(
            testFile = buildKotlinNotebookFile(name = getTestName(true), build = notebookFile),
            setupScriptDependencies = setupScriptDependencies,
            test = test,
        )
    }

    /**
     * Runs the [test] with the corresponding file loaded into the test editor.
     * The filename is determined by either [TestMetadata] annotation on the test function,
     * or from the test name (discarding the "test" prefix if present and lowercasing the first letter).
     * The directory to search for this file is specified by the [TestDataPath] annotation on the test class.
     *
     * @param setupScriptDependencies If `true` test will only continue once script dependencies are available on the classpath.
     * If `false` these are loaded asynchronously which can affect functionality like highlighting.
     *
     * @see [com.intellij.testFramework.fixtures.CodeInsightTestFixture.file].
     */
    fun runNotebookTest(setupScriptDependencies: Boolean = true, test: NotebookTestBuilder.() -> Unit) {
        // we're using TestCase.getName() to get the function name, should be safe since the test name isn't customized anywhere
        val testMetadata = this::class.java.getMethod(name).getAnnotation(TestMetadata::class.java)
        val testFile = if (testMetadata != null) {
            File(testDataPath, testMetadata.value)
        } else {
            File(testDataPath, "${getTestName(true)}.ipynb")
        }
        runNotebookTestInternal(
            testFile = testFile,
            setupScriptDependencies = setupScriptDependencies,
            test = test,
        )
    }

    /**
     * Waits until all dependencies sent from the Kernel is available on the classpath.
     * This method must not be called on [ActionUpdateThread.EDT] as it will cause a deadlock.
     */
    @RequiresBackgroundThread
    fun setUpDependenciesSynchronously(
        cellsToExecute: Int,
        updateMode: ScriptingUpdateMode = ScriptingUpdateMode.NotebookFileFocused
    ) {
        EDT
        val testCaseDisposable = newDisposable(testRootDisposable, "setUpScriptingDependencies")
        val cellEstimation = 1 + cellsToExecute // Why +1?
        val fileOrNull = if (updateMode == ScriptingUpdateMode.NotebookFileFocused) {
            backedNotebookFile
        } else null
        val updater = TestNotebookScriptsDependenciesUpdater(project, fileOrNull, cellEstimation, testCaseDisposable)
        kotlinx.coroutines.runBlocking {
            updater.setUpDependenciesSynchronously(myFixture)
        }
        waitForReadyIndexes(myFixture)
        Disposer.dispose(testCaseDisposable)
    }

    private fun runNotebookTestInternal(
        testFile: File,
        setupScriptDependencies: Boolean,
        test: NotebookTestBuilder.() -> Unit,
    ) {

        // Ideally this should be in setUp(), but moving the code causes
        // a DocumentListener leak on test teardown. It is unclear why.
        setUpWithKotlinPlugin { /* Do nothing */ }
        Disposer.register(testRootDisposable, JupyterServers.getInstance())

        withSwingMarkdownRenderMode {
            val psiTestFile = configureTestFile(testFile)
            notebookRunner = NotebookTestBuilder(project, psiTestFile, myFixture, this)
            if (setupScriptDependencies) {
                notebookRunner!!.setupScriptDependencies()
            }
            test(notebookRunner!!)
        }
    }

    private fun configureTestFile(notebookFile: File): PsiFile {
        TestLoggerFactory.enableDebugLogging(myFixture.projectDisposable, javaClass)
        myFixture.setCaresAboutInjection(true)

        // If something is executed before highlighting is invoked,
        // it may trigger daemon restarting later asynchronously
        (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

        val backedFile = myFixture.configureByJupyterFile(
            jupyterFileName = notebookFile.name,
            testDataPath = notebookFile.parentFile.absolutePath,
            isCopyToProject = false,
        )
        invokeAndWaitIfNeeded {
            myFixture.editor.setMode(NotebookEditorMode.EDIT)
        }
        originalVirtualFile = myFixture.file.virtualFile // `myFixture.file` may return the file which is injected inside one of the cells
        val testRunMode = TestContext.kernelRunMode
        invokeAndWaitIfNeeded {
            backedFile.notebook.sessionRunMode = testRunMode
            FileDocumentManager.getInstance().saveAllDocuments()
        }

        val notebookFile = runReadAction {
            FileContextUtil.getFileContext(myFixture.file)?.containingFile ?: myFixture.file
        }
        // since JDK is considered as a module dependency in K2, it should be provided in the project
        if (pluginMode == KotlinPluginMode.K2) {
            setUpProjectSDK()
        }
        return notebookFile
    }

    // During tests where the kernel runs in its own process, it will pick up the Java version from
    // the JAVA_HOME environment variable. When running locally, this can effectively be anything.
    // We set the project SDK to 21 to make it more likely that we don't run into class loading issues
    // due to the kernel compiling with a newer version of Java. Note, using 21 doesn't fix it if the
    // machine has set Java 23 or 24 in JAVA_HOME, but IdeaTestUtil only supports up to 21 for now.
    private fun setUpProjectSDK(
        sdk: Sdk = IdeaTestUtil.getMockJdk21()
    ) {
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
}
