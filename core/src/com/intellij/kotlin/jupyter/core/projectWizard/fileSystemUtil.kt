// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.kotlin.jupyter.core.jupyter.actions.CreateNotebookFactory
import com.intellij.kotlin.jupyter.core.jupyter.actions.NotebookMode
import com.intellij.kotlin.jupyter.core.language.JupyterKotlinFileType
import com.intellij.kotlin.jupyter.core.language.NotebookTemplate
import com.intellij.kotlin.jupyter.core.language.getFileTemplate
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.settings.recents.RecentNotebook
import com.intellij.kotlin.jupyter.core.settings.recents.addRecentNotebook
import com.intellij.kotlin.jupyter.core.settings.recents.rootPath
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.toAbsolutePath
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.NOTIFICATIONS_SILENT_MODE
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.IntellijInternalApi
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
import kotlinx.coroutines.launch

const val KOTLIN_NOTEBOOK_SCRATCH_PREFIX: String = "notebook"

private fun Project.runWhenProjectIsInitializedOnBGT(
    action: suspend () -> Unit
) {
    @Suppress("DEPRECATION")
    StartupManager.getInstance(this).runWhenProjectIsInitialized {
        KotlinNotebookPluginScope.getForProject(this).launch {
            action()
        }
    }
}

@OptIn(IntellijInternalApi::class)
fun createKotlinNotebookInProjectWhenProjectIsInitialized(
    project: Project,
    template: NotebookTemplate,
    notebookNamePrefix: String,
) {
    project.runWhenProjectIsInitializedOnBGT {
        val projectRoot = project.getBaseDirectories().singleOrNull() ?: error("Project root path is not found")
        val extension = JupyterKotlinFileType.getDefaultExtension()
        val freeFileName = filesInfixSequence
            .map { "$notebookNamePrefix$it.$extension" }
            .first { projectRoot.findFileOrDirectory(it) == null }

        val psiDir: PsiDirectory = readAction {
            PsiManager.getInstance(project).findDirectory(projectRoot)!!
        }

        val fileTemplate = template.getFileTemplate(project)
        val newPsiFile = withContext(Dispatchers.EDT) {
            CreateNotebookFactory.createFileFromTemplate(
                fileName = freeFileName,
                template = fileTemplate,
                directory = psiDir,
                openFileInIde = true,
                mode = NotebookMode.STANDARD,
            )
        }

        if (newPsiFile != null) {
            val projectPath = project.rootPath
            if (projectPath != null) {
                KotlinNotebookApplicationOptions.addRecentNotebook(
                    RecentNotebook(
                        newPsiFile.virtualFile,
                        projectPath,
                    )
                )
            }
        }
    }
}

object DefaultKotlinNotebookProject {
    const val NAME: String = "KotlinNotebook"

    @RequiresEdt
    suspend fun getProject(projectPath: Path): Project {
        projectPath.toFile().mkdirs()
        TrustedProjects.setProjectTrusted(projectPath, true)
        val project = ProjectManagerEx.getInstanceEx().openProjectAsync(projectPath, OpenProjectTask {
            runConfigurators = true
            isNewProject = true
            beforeInit = {
                NOTIFICATIONS_SILENT_MODE.set(it, true)
            }
        }) ?: error("Failed to open project")

        val moduleManager = ModuleManager.getInstance(project)

        val alreadyHasRootModule = moduleManager.modules.any { module ->
            module.rootManager.contentRoots.any { root ->
                root.toAbsolutePath() == projectPath
            }
        }

        if (!alreadyHasRootModule) {
            moduleManager.getOrCreateEmptyModule(projectPath, NAME)
        }

        return project
    }

    fun getProjectWithModalProgress(projectPath: Path): Project {
        @Suppress("DialogTitleCapitalization")
        return runWithModalProgressBlocking(
            ModalTaskOwner.guess(),
            KotlinNotebookBundle.message("progress.title.opening.kotlin.notebook.project"),
            TaskCancellation.Companion.cancellable()
        ) {
            val project = getProject(projectPath)

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
