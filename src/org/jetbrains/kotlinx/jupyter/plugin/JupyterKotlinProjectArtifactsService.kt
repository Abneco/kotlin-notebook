// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlinx.jupyter.plugin.util.ProjectArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.util.isNotEmptyDirectory
import org.jetbrains.plugins.notebooks.jupyter.JupyterFileType
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


enum class DependenciesState {
    PROVIDED,
    OUTDATED,
    ABSENT
}

@Service
class JupyterKotlinProjectArtifactsService(val project: Project) : Disposable {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var buildAsyncResult: Deferred<ProjectArtifacts>? = null
    private var buildResult: ProjectArtifacts? = null
    private val isBuildUpToDate: AtomicBoolean = AtomicBoolean(false)
    private val fileExtensionsOfInterest = setOf(
        KotlinFileType.INSTANCE.defaultExtension,
        "java"
    )
    @Volatile
    private var currDependenciesState = DependenciesState.PROVIDED

    fun checkProjectDependenciesStatus(): DependenciesState = currDependenciesState

    init {
        addBuildListener()
        addVFSChangesListener()
    }

    private fun addBuildListener() {
        project.service<BuildViewManager>().addListener(
            BuildProgressListener { buildId, event ->
                println("$buildId $event")
            },
            this@JupyterKotlinProjectArtifactsService
        )
    }

    private fun addVFSChangesListener() {
        val listener = object : BulkFileListener, Disposable {
            private fun isChangingEvent(event: VFileEvent): Boolean {
                val vFile = event.file ?: return false

                val fileProjects = ProjectLocator.getInstance().getProjectsForFile(vFile)
                if (project !in fileProjects) return false

                if (vFile.fileType == JupyterFileType || vFile.fileType.defaultExtension !in fileExtensionsOfInterest) return false

                return true
            }

            override fun after(events: MutableList<out VFileEvent>) {
                if (events.any { isChangingEvent(it) }) {
                    isBuildUpToDate.set(false)
                }
            }

            override fun dispose() {
            }
        }
        Disposer.register(this, listener)

        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, listener)
    }

    fun getProjectBuildResult(): ProjectArtifacts? {
        return buildResult
    }

    private fun mainModules(project: Project): List<Module> {
        fun Module.isProbablyBuildSrc() = name.split(".").any { it == "buildSrc" }

        val graph = ModuleManager.getInstance(project).moduleGraph(false)
        return mutableListOf<Module>().also { result ->
            for (node in graph.nodes) {
                if (graph.getIn(node).hasNext() || node.isProbablyBuildSrc()) continue

                val moduleRootManager = ModuleRootManager.getInstance(node)
                val sdk: Sdk? = moduleRootManager.sdk
                if (sdk == null) continue
                if (sdk.sdkType == KotlinSdkType.INSTANCE || sdk.sdkType is JavaSdkType) result.add(node)
            }
        }
    }

    @Synchronized
    private fun buildProjectAsync(): Deferred<ProjectArtifacts> {
        if (buildAsyncResult != null) return buildAsyncResult!!
        isBuildUpToDate.set(true)

        val taskManager = ProjectTaskManager.getInstance(project)

        val modulesToBuild = mainModules(project)
        if (modulesToBuild.isEmpty()) return CompletableDeferred(emptyList())

        val buildTask = taskManager.createModulesBuildTask(
            modulesToBuild.toTypedArray(),
            true,
            true,
            false
        )

        val buildTaskContext = ProjectTaskContext().apply {
            enableCollectionOfGeneratedFiles()
        }

        val resultPromise = taskManager.run(buildTaskContext, buildTask).then {
            val allModules = ModuleManager.getInstance(project).modules
            val hasErrors = it.hasErrors()

            val projectJarPaths = mutableListOf<String>().also { paths ->
                CompilerPaths.getOutputPaths(allModules).forEach { path ->
                    paths.add(path)
                    val javaOutput = "classes${File.separatorChar}java"
                    val kotlinOutput = "classes${File.separatorChar}kotlin"
                    if (path.contains(javaOutput)) {
                        paths.add(path.replace(javaOutput, kotlinOutput))
                    }
                }
            }.filter { File(it).exists() }

            if (hasErrors) {
                val isEmpty = projectJarPaths.none { File(it).isNotEmptyDirectory }
                if (!isEmpty) {
                    currDependenciesState = DependenciesState.OUTDATED
                } else currDependenciesState = DependenciesState.ABSENT
            } else currDependenciesState = DependenciesState.PROVIDED

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
        }.also {
            buildAsyncResult = it
        }
    }

    suspend fun buildProject(): ProjectArtifacts {
        val deferred = buildAsyncResult
        return if (deferred != null && (deferred.isCompleted && isBuildUpToDate.get() || !deferred.isCompleted)) {
            deferred.await()
        } else {
            buildAsyncResult = null
            buildProjectAsync().await()
        }
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