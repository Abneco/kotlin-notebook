// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook

import com.intellij.openapi.fileEditor.FileEditorManager
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
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService.Companion.buildProjectAndGetLibraries
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookDependencies
import org.jetbrains.kotlinx.jupyter.plugin.settings.projectDependencies
import org.jetbrains.kotlinx.jupyter.plugin.settings.projectLibraries
import org.jetbrains.kotlinx.jupyter.plugin.test.createEmptyNotebook
import org.jetbrains.kotlinx.jupyter.plugin.test.delete
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.core.impl.file.notebook
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

class KotlinNotebookModuleDependenciesTest : UsefulTestCase() {
    private lateinit var fixture: CodeInsightTestFixture
    private lateinit var modulesDirectory: Path
    private var _notebookVirtualFile: BackedNotebookVirtualFile? = null

    private val project get() = fixture.project
    private val notebookVirtualFile get() = _notebookVirtualFile!!

    fun `test one module`() {
        doTest(listOf(ModuleManager.getInstance(project).modules.first()))
    }

    fun `test two modules`() {
        doTest(ModuleManager.getInstance(project).modules.toList().drop(1))
    }

    fun `test all modules`() {
        doTest(ModuleManager.getInstance(project).modules.toList())
    }

    private fun doTest(modules: List<Module>) {
        runInEdtAndWait {
            notebookVirtualFile.notebook.projectDependencies = KotlinNotebookDependencies.fromModules(modules)
            notebookVirtualFile.notebook.projectLibraries = KotlinNotebookDependencies.None

            FileEditorManager.getInstance(project).openFile(notebookVirtualFile.file)
        }

        val classpath = runBlocking {
            JupyterKotlinProjectArtifactsService.getInstance(project).buildProjectAndGetLibraries(notebookVirtualFile)
        }

        val moduleClassPaths = modules.flatMap {
            ModuleRootManager.getInstance(it).orderEntries().withoutSdk().classes().pathsList.pathList
        }
        assertEquals(moduleClassPaths, classpath)
    }

    override fun setUp() {
        runInEdtAndWait {
            super.setUp()

            val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
            fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture())

            modulesDirectory = TemporaryDirectory.generateTemporaryPath("modules")
            repeat(3) { projectBuilder.createModule(modulesDirectory) }

            fixture.setUp()

            _notebookVirtualFile = project.createEmptyNotebook("test.ipynb")
        }
    }

    override fun tearDown() {
        runInEdtAndWait {
            listOf(
                { JavaAwareProjectJdkTableImpl.removeInternalJdkInTests() }, // remove internal jdk created by BuildManager
                { notebookVirtualFile.delete() },
                { fixture.tearDown() },
                { modulesDirectory.deleteRecursively() },
                { super.tearDown() },
            ).forEachGuaranteed { it() }
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