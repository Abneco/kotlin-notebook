// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asDeferred
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.util.ProjectArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.util.isNotEmptyDirectory
import org.jetbrains.kotlinx.jupyter.plugin.util.parentsWithSelf
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


enum class DependenciesState {
    PROVIDED,
    OUTDATED,
    ABSENT
}

@Service(Service.Level.PROJECT)
class JupyterKotlinProjectArtifactsService(val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
    private var buildAsyncResult: Deferred<ProjectArtifacts> = CompletableDeferred(emptyList())
    private val isBuildUpToDate: AtomicBoolean = AtomicBoolean(false)
    private val fileExtensionsOfInterest = setOf(
        // source files
        "kt",
        "java",

        // build script files
        "kts",
        "gradle",
    )

    @Volatile
    private var currDependenciesState = DependenciesState.PROVIDED
    private val accessLock = ReentrantLock()

    fun checkProjectDependenciesStatus(): DependenciesState = currDependenciesState

    init {
        addBuildListener()
        addVFSChangesListener()
    }

    private fun addBuildListener() {
        project.service<BuildViewManager>().addListener(
            BuildProgressListener { buildId, event ->
                LOG.debug("$buildId $event")
            },
            this@JupyterKotlinProjectArtifactsService
        )
    }

    private fun addVFSChangesListener() {
        val listener = object : BulkFileListener {
            private fun isChangingEvent(event: VFileEvent): Boolean {
                val vFile = event.file ?: return false

                val fileProjects = ProjectLocator.getInstance().getProjectsForFile(vFile)
                if (project !in fileProjects) return false

                // TODO: reconsider this approach, maybe create extra option
                if (vFile.parentsWithSelf.any { it.isDirectory && it.name == "generated" }) return false
                return vFile.extension in fileExtensionsOfInterest
            }

            override fun after(events: List<VFileEvent>) {
                if (events.any { isChangingEvent(it) }) {
                    isBuildUpToDate.set(false)
                }
            }
        }
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, listener)
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

    private fun getProjectFiles(): Promise<ProjectArtifacts> {
        val taskManager = ProjectTaskManager.getInstance(project)

        val modulesToBuild = mainModules(project)
        if (modulesToBuild.isEmpty()) return resolvedPromise(emptyList())

        val buildTask = taskManager.createModulesBuildTask(modulesToBuild.toTypedArray(), true, true, false)
        val buildTaskContext = ProjectTaskContext().apply {
            enableCollectionOfGeneratedFiles()
        }

        return taskManager.run(buildTaskContext, buildTask).then { buildResult ->
            val allModules = ModuleManager.getInstance(project).modules

            val projectJarPaths = mutableListOf<String>()
                .also { paths ->
                    CompilerPaths.getOutputPaths(allModules).forEach { path ->
                        paths.add(path)
                        val javaOutput = "classes${File.separatorChar}java"
                        val kotlinOutput = "classes${File.separatorChar}kotlin"
                        if (path.contains(javaOutput)) {
                            paths.add(path.replace(javaOutput, kotlinOutput))
                        }
                    }
                }
                .distinct()
                .filter { File(it).exists() }

            currDependenciesState = if (!buildResult.hasErrors()) DependenciesState.PROVIDED
            else if (projectJarPaths.any { File(it).isNotEmptyDirectory }) DependenciesState.OUTDATED
            else DependenciesState.ABSENT

            projectJarPaths
        }
    }

    private fun getLibraryFiles(): ProjectArtifacts {
        return LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.filter {
            it.name != JupyterCompilerService.scriptDependenciesLibName
        }.flatMap { library ->
            library.getFiles(OrderRootType.CLASSES)
                // nio can't be used here since JarFileSystemImpl#getNioPath returns null for a jar root file
                .map { VfsUtilCore.virtualToIoFile(it) }
                .filter {
                    try {
                        it.exists()
                    } catch (_: SecurityException) {
                        false
                    }
                }
                .map { it.absolutePath }
        }
    }

    suspend fun buildProject(): ProjectArtifacts {
        val options = KotlinNotebookProjectOptionsProvider.getInstance(project).state
        if (!options.shouldBuildProject) return emptyList()

        val deferredArtifacts = accessLock.withLock {
            val deferred = buildAsyncResult
            if (deferred.isCompleted && !isBuildUpToDate.get()) {
                isBuildUpToDate.set(true)

                val projectFiles = getProjectFiles()
                val allFiles = if (options.shouldAddProjectLibrariesToClasspath) {
                    projectFiles.then { it + getLibraryFiles() }
                } else projectFiles

                buildAsyncResult = allFiles.asDeferred()
            }
            buildAsyncResult
        }
        return deferredArtifacts.await()
    }

    override fun dispose() = Unit

    companion object {
        private val LOG = logger<JupyterKotlinProjectArtifactsService>()

        fun getInstance(project: Project): JupyterKotlinProjectArtifactsService {
            return project.service()
        }
    }
}