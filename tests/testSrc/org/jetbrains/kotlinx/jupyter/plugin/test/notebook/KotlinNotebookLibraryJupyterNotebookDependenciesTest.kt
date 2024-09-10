// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.test.notebook

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.util.containers.forEachGuaranteed
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.JupyterKotlinProjectArtifactsService.Companion.buildProjectAndGetLibraries
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookDependencies
import org.jetbrains.kotlinx.jupyter.plugin.settings.projectDependencies
import org.jetbrains.kotlinx.jupyter.plugin.settings.projectLibraries
import org.jetbrains.kotlinx.jupyter.plugin.test.createEmptyNotebook
import org.jetbrains.kotlinx.jupyter.plugin.test.delete
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString


class KotlinNotebookLibraryDependenciesTest : UsefulTestCase() {
    private lateinit var fixture: IdeaProjectTestFixture
    private lateinit var librariesDirectory: Path
    private var _notebookVirtualFile: BackedNotebookVirtualFile? = null

    private val project get() = fixture.project
    private val projectLibraries get() = LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.toList()
    private val notebookVirtualFile get() = _notebookVirtualFile!!

    fun `test one library`() {
        doTest(listOf(projectLibraries.first()))
    }

    fun `test two libraries`() {
        doTest(projectLibraries.slice(listOf(1, 2)))
    }

    fun `test all libraries`() {
        doTest(projectLibraries)
    }

    private fun doTest(libraries: List<Library>) {
        notebookVirtualFile.notebook.projectDependencies = KotlinNotebookDependencies.None
        notebookVirtualFile.notebook.projectLibraries = KotlinNotebookDependencies.fromLibraries(libraries)

        FileEditorManager.getInstance(project).openFile(notebookVirtualFile.file)

        val classpath = runWithModalProgressBlocking(project, "building dependencies for ${notebookVirtualFile.file.name}") {
            JupyterKotlinProjectArtifactsService.getInstance(project).buildProjectAndGetLibraries(notebookVirtualFile)
        }
        assertEquals(
            libraries.flatMap { it.getFiles(OrderRootType.CLASSES).toList() }.map { it.path },
            classpath.map { it.replace('\\', '/') }
        )
    }

    override fun setUp() {
        super.setUp()

        fixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name).getFixture()
        fixture.setUp()

        librariesDirectory = TemporaryDirectory.generateTemporaryPath("libraries")
        repeat(3) {
            val classesDirectory = librariesDirectory.resolve("classes$it")
            createLibrary("lib$it", classesDirectory)
        }

        _notebookVirtualFile = project.createEmptyNotebook("test.ipynb")
    }

    override fun tearDown() {
        listOf(
            { notebookVirtualFile.delete() },
            { fixture.tearDown() },
            { librariesDirectory.deleteRecursively() },
            { super.tearDown() },
        ).forEachGuaranteed { it() }
    }

    private fun createLibrary(name: String, classesPath: Path) {
        val projectLibrariesModel = LibraryTablesRegistrar.getInstance().getLibraryTable(project).modifiableModel
        val libraryModel = (projectLibrariesModel.createLibrary(name) as LibraryEx).modifiableModel

        Files.createDirectories(classesPath)
        val classesVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(classesPath.pathString)!!

        libraryModel.addRoot(classesVirtualFile, OrderRootType.CLASSES)

        runWriteActionAndWait {
            libraryModel.commit()
            projectLibrariesModel.commit()
        }
    }
}