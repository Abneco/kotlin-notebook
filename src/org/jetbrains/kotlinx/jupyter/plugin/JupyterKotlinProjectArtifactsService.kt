// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.jetbrains.kotlinx.jupyter.plugin.util.ProjectArtifacts
import java.io.File
import java.util.concurrent.TimeUnit

@Service
class JupyterKotlinProjectArtifactsService(val project: Project) : Disposable {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var buildAsyncResult: Deferred<ProjectArtifacts>? = null
    private var buildResult: ProjectArtifacts? = null

    init {
        addBuildListener()
    }

    private fun addBuildListener() {
        project.service<BuildViewManager>().addListener(
            BuildProgressListener { buildId, event ->
                println("$buildId $event")
            },
            this@JupyterKotlinProjectArtifactsService
        )
    }

    fun getProjectBuildResult(): ProjectArtifacts? {
        return buildResult
    }

    @Synchronized
    private fun buildProjectAsync(): Deferred<ProjectArtifacts> {
        if (buildAsyncResult != null) return buildAsyncResult!!

        val taskManager = ProjectTaskManager.getInstance(project)

        val buildTask = taskManager.createAllModulesBuildTask(true, project)
        val buildTaskContext = ProjectTaskContext().apply {
            enableCollectionOfGeneratedFiles()
        }

        val resultPromise  = taskManager.run(buildTaskContext, buildTask).then {
            val allModules = ModuleManager.getInstance(project).modules
            val projectJarPaths = CompilerPaths.getOutputPaths(allModules).filter { File(it).exists() }

            val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
            val librariesClassesPaths = libraryTable.libraries.flatMap { library ->
                library
                    .getFiles(OrderRootType.CLASSES)
                    .filter { it.isInLocalFileSystem }
                    .mapNotNull { it.fileSystem.getNioPath(it)?.toRealPath()?.toString() }
            }

            val allPaths = projectJarPaths + librariesClassesPaths
            allPaths
        }

        return coroutineScope.async {
            val res = resultPromise.blockingGet(1, TimeUnit.DAYS).orEmpty()
            buildResult = res
            res
        }
    }

    suspend fun buildProject(): ProjectArtifacts {
        return buildProjectAsync().await()
    }

    override fun dispose() {
        coroutineScope.cancel()
    }

    companion object {
        fun getInstance(project: Project): JupyterKotlinProjectArtifactsService {
            return project.service()
        }
    }
}