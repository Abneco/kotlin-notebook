// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test

import com.intellij.injected.editor.EditorWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.editor.setHeaderEditingAllowed
import com.intellij.jupyter.core.jupyter.connections.server.JupyterServers
import com.intellij.kotlin.jupyter.core.logging.KotlinNotebookLoggerFactory
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.core.settings.sessionRunMode
import com.intellij.kotlin.jupyter.test.runners.KotlinNotebookTestRunner
import com.intellij.kotlin.jupyter.test.runners.ListenableTest
import com.intellij.kotlin.jupyter.test.runners.ListenableTestImpl
import com.intellij.kotlin.jupyter.test.runners.findAnnotationInHierarchy
import com.intellij.kotlin.jupyter.test.util.data.TEMPLATE_DATA_EXTENSION
import com.intellij.kotlin.jupyter.test.util.fromTemplateFile
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
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
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.forEachGuaranteed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.debug.junit4.CoroutinesTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.NonNls
import org.jetbrains.jupyter.builder.NotebookBuilder
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.notebooks.tests.JupyterBaseTestCase
import org.jetbrains.plugins.notebooks.tests.cleanJupyterUserData
import org.jetbrains.plugins.notebooks.tests.configureByJupyterFile
import org.jetbrains.plugins.notebooks.tests.withSwingMarkdownRenderMode
import org.junit.Assume
import org.junit.Rule
import org.junit.rules.DisableOnDebug
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for all notebook tests. The entry point is the [runNotebookTest] method which provides
 * access to an API wrapper making it possible to write tests in a more human-readable way.
 */
@RunWith(KotlinNotebookTestRunner::class)
abstract class KotlinNotebookTestCase :
    JupyterBaseTestCase(),
    ExpectedPluginModeProvider,
    ListenableTest by ListenableTestImpl()
{
    @JvmField
    @Rule
    var timeout: TestRule = DisableOnDebug(
        CoroutinesTimeout.seconds(180, cancelOnTimeout = true)
    )

    // We cannot run on the EDT thread as Kernel Execution also runs there, which can result in deadlocks
    // when waiting for kernel status messages.
    override fun runInDispatchThread(): Boolean = false

    companion object {
        private const val CONTENT_ROOT_VARIABLE: @NonNls String = $$"$CONTENT_ROOT"
        private const val CONTENT_ROOT: @NonNls String = "/plugins/kotlin/jupyter/tests/unitTests"
        private const val PROJECT_ROOT_VARIABLE: @NonNls String = $$"$PROJECT_ROOT"
        private const val PROJECT_ROOT: @NonNls String = ""
    }

    private var notebookRunner: NotebookTestBuilder? = null
    override lateinit var originalVirtualFile: VirtualFile

    override val pluginMode: KotlinPluginMode
        get() {
            val vmValue = System.getProperty("idea.kotlin.plugin.use.k1") ?: return KotlinPluginMode.K2
            return when (vmValue) {
                "true" -> KotlinPluginMode.K1
                else -> KotlinPluginMode.K2
            }
        }

    protected val backedNotebookFile: BackedNotebookVirtualFile
        get() = when (val file = myFixture.kotlinNotebookFile) {
            is BackedNotebookVirtualFile -> file
            else -> error("Null notebook file found for ${myFixture.file?.virtualFile}")
        }

    override fun setUp() {
        wrapSetUp(this) {
            super.setUp()
            KotlinNotebookLoggerFactory.enableUnitTestMode()
            setHeaderEditingAllowed(false, testRootDisposable)
        }
    }

    override fun tearDown() {
        // We attempt to run as much teardown logic as possible.
        // This is mostly done in an attempt to close any threads or thread pools,
        // as they will trigger an assertion in com.intellij.testFramework.common.ThreadLeakTracker.
        wrapTearDown(this) {
            try {
                listOf(
                    { myFixture.cleanJupyterUserData() },
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
        val testDataPath = this::class.java.findAnnotationInHierarchy<TestDataPath>()?.value
        val testMetadataPath = this::class.java.findAnnotationInHierarchy<TestMetadata>()?.value
        return FileUtilRt
            .toSystemIndependentName(
                listOfNotNull(testDataPath, testMetadataPath).joinToString(FileSystems.getDefault().separator)
            )
            .replace(CONTENT_ROOT_VARIABLE, CONTENT_ROOT)
            .replace(PROJECT_ROOT_VARIABLE, PROJECT_ROOT)
    }

    /**
     * Returns the path to the original file being used for this notebook test.
     *
     * In the case of ".ktnb" files, it is the template file that is returned and not the .ipynb file generated
     * from it.
     */
    fun getTestFile(): Path {
        // we're using TestCase.getName() to get the function name, should be safe since the test name isn't customized anywhere
        val testMetadata = this::class.java.getMethod(name).getAnnotation(TestMetadata::class.java)
        return if (testMetadata != null) {
            Path(testDataPath, testMetadata.value)
        } else {
            val testFileName = "${getTestName(true)}.$TEMPLATE_DATA_EXTENSION"
            val completePath = computeCompletePathFromParentToFile(testFileName)
            if (completePath == null) {
                error("Can't find the requested file '$testFileName' with parent path: $testDataPath")
            }
            completePath.toAbsolutePath()
        }
    }

    /**
     * Returns the path to the expected outcome of the test. If the file doesn't exist, an error is thrown.
     *
     * The expected outcome file is required to be placed next to the test file (as returned by [getTestFile]).
     */
    fun getExpectedTestFile(fileExtension: String = "kt.expected"): Path {
        val testFile = getTestFile()
        val expectedFile = testFile.parent.resolve(testFile.name.replaceAfterLast(".", fileExtension))
        if (!expectedFile.exists()) {
            error("Expected outcome file doesn't exist: $expectedFile")
        }
        return expectedFile
    }

    /**
     * Returns the expected outcome of the test. If the file doesn't exist, an error is thrown.
     * See [getExpectedTestFile] for more details.
     */
    fun getExpectedTestFileContent(): String {
        return getExpectedTestFile().readText()
    }

    /**
     * Performs search from a top-level directory inside child directories for a particular file.
     * Returns immediately if the search is not necessary.
     */
    private fun computeCompletePathFromParentToFile(fileName: String): Path? {
        val file = Path.of(testDataPath, fileName).absolute()
        return if (file.exists() && file.isRegularFile()) {
            file
        } else {
            findPathFromParentToFile(fileName)
        }
    }

    private fun findPathFromParentToFile(fileName: String): Path? {
        return Files.walk(Paths.get(testDataPath)).use { paths ->
            paths.filter { it.fileName.toString() == fileName }
                .findFirst()
                .orElse(null)
        }
    }



    /**
     * Runs the [test] with the corresponding file loaded into the test editor.
     * The filename is determined by either [TestMetadata] annotation on the test function,
     * or from the test name (discarding the "test" prefix if present and lowercasing the first letter).
     * The directory to search for this file is specified by the [TestDataPath] annotation on the test class.
     * Once the template file is found, [NotebookBuilder] is used to construct the real notebook.
     *
     * @param setupScriptDependencies If `true` test will only continue once script dependencies are available on the classpath.
     * If `false` these are loaded asynchronously which can affect functionality like highlighting.
     * @param timeout Timeout for the test execution, if `null`. The global [KotlinNotebookTestCase.timeout] is used instead.
     * @see [com.intellij.testFramework.fixtures.CodeInsightTestFixture.file].
     */
    fun runNotebookTest(
        setupScriptDependencies: Boolean = true,
        timeout: Duration? = null,
        test: suspend NotebookTestBuilder.() -> Unit
    ) {
        Assume.assumeTrue(
            "IDE process tests are disabled, see ...",
            testContext.kernelRunMode != KotlinNotebookSessionRunMode.IDE_PROCESS,
        )
        
        // Notebook tests requires a Notebook Template File (.ktnb) to be present.
        var testFile = getTestFile()
        if (testFile.name.endsWith(TEMPLATE_DATA_EXTENSION)) {
            testFile = buildKotlinNotebookFile(
                name = getTestName(true),
                build = {
                    fromTemplateFile(testFile)
                }
            )
        }
        runNotebookTestInternal(
            testFile = testFile,
            setupScriptDependencies = setupScriptDependencies,
            timeout = timeout,
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
        val testCaseDisposable = newDisposable(testRootDisposable, "setUpScriptingDependencies")
        val cellEstimation = 1 + cellsToExecute // Why +1?
        val fileOrNull = if (updateMode == ScriptingUpdateMode.NotebookFileFocused) {
            backedNotebookFile
        } else null
        val updater = TestNotebookScriptsDependenciesUpdater(project, fileOrNull, cellEstimation, testCaseDisposable)
        runBlocking {
            updater.setUpDependenciesSynchronously(myFixture)
        }
        waitForReadyIndexes(myFixture)
        Disposer.dispose(testCaseDisposable)
    }

    protected open fun additionalSetup(builder: NotebookTestBuilder) {
        KotlinTestHelpers.registerChooserInterceptor(myFixture.testRootDisposable)
    }

    protected fun assertTestFileHasCaret() {
        val rawText = FileUtilRt.loadFile(getTestFile().toFile(), true)
        assertTrue("\"<caret>\" is missing in file \"${file.name}\"", rawText.contains("<caret>"))
    }

    private fun runNotebookTestInternal(
        testFile: Path,
        setupScriptDependencies: Boolean,
        timeout: Duration?,
        test: suspend NotebookTestBuilder.() -> Unit,
    ) {

        // Ideally this should be in setUp(), but moving the code causes
        // a DocumentListener leak on test teardown. It is unclear why.
        setUpWithKotlinPlugin { /* Do nothing */ }
        Disposer.register(testRootDisposable, JupyterServers.getInstance())

        withSwingMarkdownRenderMode {
            val psiTestFile = configureTestFile(testFile)
            notebookRunner = NotebookTestBuilder(project, psiTestFile, myFixture, this)
            additionalSetup(notebookRunner!!)
            if (setupScriptDependencies) {
                notebookRunner!!.setupScriptDependencies()
            }

            // We run `async` in a separate scope so that `runBlocking` does not block after the timeout is over
            val testScope = createCoroutineScope()
            runBlocking {
                try {
                    withTimeout(timeout ?: Int.MAX_VALUE.seconds) {
                        testScope.async {
                            test(notebookRunner!!)
                        }.await()
                    }
                } finally {
                    testScope.cancel()
                }
            }
        }
    }

    private fun configureTestFile(notebookFile: Path): PsiFile {
        TestLoggerFactory.enableDebugLogging(myFixture.projectDisposable, javaClass)
        myFixture.setCaresAboutInjection(true)

        // If something is executed before highlighting is invoked,
        // it may trigger daemon restarting later asynchronously
        (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

        invokeAndWaitIfNeeded {
            val backedFile = myFixture.configureByJupyterFile(
                jupyterFileName = notebookFile.name,
                testDataPath = notebookFile.parent.toAbsolutePath().pathString,
            )
            myFixture.editor.setMode(NotebookEditorMode.EDIT)
            originalVirtualFile = myFixture.file.virtualFile
            // `myFixture.file` may return the file which is injected inside one of the cells
            backedFile.notebook.sessionRunMode = testContext.kernelRunMode
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

    @RequiresReadLock
    fun getKtFileUnderCaret(): KtFile? {
        val hostFile = backedNotebookFile.file.findPsiFile(project) ?: return null
        val manager = InjectedLanguageManager.getInstance(project)
        val hostOffset = when (val fileEditor = editor) {
            is EditorWindow -> manager.injectedToHost(
                fileEditor.injectedFile, 0
            )
            else -> 0
        }

        return manager
            .findInjectedElementAt(hostFile, myFixture.caretOffset + hostOffset)
            ?.containingFile as? KtFile
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

    /**
     * Inspired by com.android.tools.idea.concurrency.CoroutineUtils, but will create
     * a scope with [Job], so any failure will cancel the whole test.
     */
    private fun createCoroutineScope(
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        extraContext: CoroutineContext = EmptyCoroutineContext,
    ): CoroutineScope {
        val job = Job()
        @Suppress("RAW_SCOPE_CREATION")
        return CoroutineScope(job + dispatcher + extraContext)
    }

}
