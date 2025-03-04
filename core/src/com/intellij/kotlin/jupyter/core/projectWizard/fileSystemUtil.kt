// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsLocator
import com.intellij.kotlin.jupyter.core.jupyter.actions.CreateNotebookFactory
import com.intellij.kotlin.jupyter.core.jupyter.actions.NotebookMode
import com.intellij.kotlin.jupyter.core.language.JupyterKotlinFileType
import com.intellij.kotlin.jupyter.core.language.NotebookTemplate
import com.intellij.kotlin.jupyter.core.language.getFileTemplate
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findFileOrDirectory
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

const val KOTLIN_NOTEBOOK_SCRATCH_PREFIX: String = "notebook"

@OptIn(IntellijInternalApi::class)
fun createKotlinNotebookInProjectWhenProjectIsInitialized(
    project: Project,
    template: NotebookTemplate = NotebookTemplate.EMPTY,
    notebookNamePrefix: String = KOTLIN_NOTEBOOK_SCRATCH_PREFIX,
) {
    @Suppress("DEPRECATION")
    StartupManager.getInstance(project).runWhenProjectIsInitialized {
        val projectRoot = project.getBaseDirectories().singleOrNull() ?: error("Project root path is not found")
        val extension = JupyterKotlinFileType.getDefaultExtension()
        val freeFileName = filesInfixSequence
            .map { "$notebookNamePrefix$it.$extension" }
            .first { projectRoot.findFileOrDirectory(it) == null }

        val psiDir: PsiDirectory = PsiManager.getInstance(project).findDirectory(projectRoot)!!
        val fileTemplate = template.getFileTemplate(project)
        CreateNotebookFactory.createFileFromTemplate(
            fileName = freeFileName,
            template = fileTemplate,
            directory = psiDir,
            openFileInIde = true,
            mode = NotebookMode.STANDARD,
        )
    }
}

object DefaultKotlinNotebookProject {
    const val NAME: String = "KotlinNotebook"

    val rootPath: Path by lazy {
        Path.of(
            System.getProperty("user.home"),
            ".kotlinNotebook",
            NAME
        )
    }

    fun createRootPath(): Path {
        return rootPath.apply {
            createDirectories()
        }
    }

    fun getVirtualFileRoot(): VirtualFile {
        return VfsUtil.createDirectories(createRootPath().absolutePathString())
    }

    @RequiresEdt
    suspend fun getProject(): Project {
        val projectPath = createRootPath()
        TrustedProjects.setProjectTrusted(
            locatedProject = TrustedProjectsLocator.locateProject(projectPath, null),
            isTrusted = true
        )
        val project = ProjectManagerEx.getInstanceEx().openProjectAsync(projectPath, OpenProjectTask {
            runConfigurators = true
            isNewProject = true
        }) ?: error("Failed to open project")
        ModuleManager.getInstance(project)
            .getOrCreateEmptyModule(projectPath, NAME)

        return project
    }

    fun getProjectWithModalProgress(): Project {
        @Suppress("DialogTitleCapitalization")
        return runWithModalProgressBlocking(
            ModalTaskOwner.guess(),
            KotlinNotebookBundle.message("progress.title.opening.kotlin.notebook.project"),
            TaskCancellation.Companion.cancellable()
        ) {
            val project = getProject()

            // Wait for project initialization
            @Suppress("DEPRECATION")
            StartupManager.getInstance(project).runWhenProjectIsInitialized {
                // Project is ready
            }

            project
        }
    }
}

private val filesInfixSequence = sequence {
    yield("")
    var i = 1
    while (true) {
        yield("_$i")
        i++
    }
}

private suspend fun ModuleManager.getOrCreateEmptyModule(rootModulePath: Path, name: String) {
    if (findModuleByName(name) != null) return
    withContext(Dispatchers.EDT) {
        WriteAction.run<Throwable> {
            val module = newModule(
                rootModulePath.resolve("$name.iml").absolutePathString(),
                JavaModuleType.getModuleType().id
            )
            with(ModuleRootManager.getInstance(module).modifiableModel) {
                try {
                    addContentEntry(rootModulePath.absolutePathString())
                    inheritSdk()
                    commit()
                } finally {
                    dispose()
                }
            }
        }
    }
}
