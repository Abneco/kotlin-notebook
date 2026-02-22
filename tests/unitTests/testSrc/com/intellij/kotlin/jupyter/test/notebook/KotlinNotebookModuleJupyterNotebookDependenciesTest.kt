// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.projectModel.JupyterKotlinProjectArtifactsService
import com.intellij.kotlin.jupyter.core.projectModel.JupyterKotlinProjectArtifactsService.Companion.buildProjectAndGetLibraries
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookDependencies
import com.intellij.kotlin.jupyter.core.settings.notebookDependencies
import com.intellij.kotlin.jupyter.test.createEmptyNotebook
import com.intellij.kotlin.jupyter.test.delete
import com.intellij.kotlin.jupyter.test.runners.KotlinNotebookTestRunner
import com.intellij.kotlin.jupyter.test.runners.ListenableTest
import com.intellij.kotlin.jupyter.test.runners.ListenableTestImpl
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.util.containers.forEachGuaranteed
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

@RunWith(KotlinNotebookTestRunner::class)
class KotlinNotebookModuleDependenciesTest :
    UsefulTestCase(),
    ListenableTest by ListenableTestImpl()
{
    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var modulesDirectory: Path
    private var _notebookVirtualFile: BackedNotebookVirtualFile? = null

    private val project get() = fixture.project
    private val notebookVirtualFile get() = _notebookVirtualFile!!

    @Test
    fun `test one module`() {
        doTest(ModuleManager.getInstance(project).modules.first())
    }

    @Test
    fun `test no modules`() {
        doTest(module = null)
    }

    private fun doTest(module: Module?) {
        runInEdtAndWait {
            DaemonCodeAnalyzer.getInstance(project).disableUpdateByTimer(testRootDisposable)
            notebookVirtualFile.notebook.notebookDependencies = if (module != null) {
                KotlinNotebookDependencies.SingleModule(module)
            } else {
                KotlinNotebookDependencies.None
            }
        }

        val classpath = runBlocking {
            JupyterKotlinProjectArtifactsService.getInstance(project).buildProjectAndGetLibraries(notebookVirtualFile)
        }

        val moduleClassPaths = module?.let {
            ModuleRootManager.getInstance(it).orderEntries().withoutSdk().classes().pathsList.pathList
        } ?: emptyList()
        assertEquals(moduleClassPaths, classpath)
    }

    override fun setUp() {
        runInEdtAndWait {
            wrapSetUp(this) {
                super.setUp()

                val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
                fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture())

                modulesDirectory = TemporaryDirectory.generateTemporaryPath("modules")
                repeat(3) { projectBuilder.createModule(modulesDirectory) }

                fixture.setUp()

                _notebookVirtualFile = project.createEmptyNotebook("test.ipynb", testRootDisposable)
            }
        }
    }

    override fun tearDown() {
        runInEdtAndWait {
            wrapTearDown(this) {
                listOf(
                    {
                        @Suppress("UsagesOfObsoleteApi")
                        runWriteAction {
                            FileDocumentManager.getInstance().saveAllDocuments()
                        }
                    },
                    { JavaAwareProjectJdkTableImpl.removeInternalJdkInTests() }, // remove internal jdk created by BuildManager
                    { notebookVirtualFile.delete() },
                    { fixture.tearDown() },
                    { modulesDirectory.deleteRecursively() },
                    { super.tearDown() },
                ).forEachGuaranteed { it() }
            }
        }
    }

    // Since JpsProjectTaskRunner.run calls ModalityUiUtil.invokeLaterIfNeeded, using EDT + runWithModalProgressBlocking won't work
    override fun runInDispatchThread(): Boolean = false

    private fun TestFixtureBuilder<IdeaProjectTestFixture>.createModule(basePath: Path) {
        val modulesRoot = TemporaryDirectory.generateTemporaryPath("module", basePath)
        val sourcesDirectory = modulesRoot.resolve("src")
        val classesDirectory = modulesRoot.resolve("classes")

        Files.createDirectories(modulesRoot)
        Files.createDirectories(sourcesDirectory)
        Files.createDirectories(classesDirectory)

        addModule(JavaModuleFixtureBuilder::class.java)
            .addContentRoot(modulesRoot.pathString)
            .addSourceRoot(sourcesDirectory.pathString)
            .setOutputPath(classesDirectory.pathString)
    }
}