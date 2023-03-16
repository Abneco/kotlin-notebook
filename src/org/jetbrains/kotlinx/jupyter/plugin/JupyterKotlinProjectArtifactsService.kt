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
import com.intellij.openapi.util.Disposer
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
import kotlinx.coroutines.async
import org.jetbrains.concurrency.asDeferred
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.util.ProjectArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.util.isNotEmptyDirectory
import org.jetbrains.kotlinx.jupyter.plugin.util.parentsWithSelf
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


enum class DependenciesState {
    PROVIDED,
    OUTDATED,
    ABSENT
}

data class BuildResult(val artifacts: ProjectArtifacts, val state: DependenciesState) {
    companion object {
        val EMPTY = BuildResult(emptyList(), DependenciesState.PROVIDED)
    }
}

@Service(Service.Level.PROJECT)
class JupyterKotlinProjectArtifactsService(val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
    private val buildResultCache: BaseCache<BuildResult> = BuildResultCache(project, this)
    private val librariesCache: BaseCache<ProjectArtifacts> = LibrariesCache(project, coroutineScope)

    private val fileExtensionsOfInterest = setOf(
        // source files
        "kt",
        "java",

        // build script files
        "kts",
        "gradle",
    )

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
                    buildResultCache.markOutdated()
                    librariesCache.markOutdated()
                }
            }
        }
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, listener)
    }

    suspend fun buildProject(): BuildResult {
        val options = KotlinNotebookProjectOptionsProvider.getInstance(project).state
        val isBuildProject = options.shouldBuildProject
        if (!isBuildProject) return BuildResult.EMPTY

        return buildResultCache.getValue()
    }

    suspend fun getLibraries(): ProjectArtifacts {
        val options = KotlinNotebookProjectOptionsProvider.getInstance(project).state
        val isAddLibraries = options.shouldAddProjectLibrariesToClasspath
        if (!isAddLibraries) return emptyList()

        return librariesCache.getValue()
    }

    override fun dispose() = Unit

    private class BuildResultCache(private val project: Project, parent: Disposable) : BaseCache<BuildResult>(BuildResult.EMPTY),
                                                                                       Disposable {
        init {
            Disposer.register(parent, this)
        }

        override fun loadValue(): Deferred<BuildResult> {
            val taskManager = ProjectTaskManager.getInstance(project)

            val modulesToBuild = mainModules(project)
            if (modulesToBuild.isEmpty()) return CompletableDeferred(BuildResult.EMPTY)

            val buildTask = taskManager.createModulesBuildTask(modulesToBuild.toTypedArray(), true, true, false)
            val buildTaskContext = ProjectTaskContext().apply {
                enableCollectionOfGeneratedFiles()
            }

            val deferredResult = taskManager.run(buildTaskContext, buildTask).then { buildResult ->
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

                val state = if (!buildResult.hasErrors()) DependenciesState.PROVIDED
                else if (projectJarPaths.any { File(it).isNotEmptyDirectory }) DependenciesState.OUTDATED
                else DependenciesState.ABSENT

                BuildResult(projectJarPaths, state)
            }.asDeferred()
            deferredResult.cancelOnDispose(this)
            return deferredResult
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

        override fun dispose() {
            clear()
        }
    }

    private class LibrariesCache(private val project: Project, val coroutineScope: CoroutineScope) :
        BaseCache<ProjectArtifacts>(emptyList()) {
        override fun loadValue(): Deferred<ProjectArtifacts> {
            return coroutineScope.async {
                LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.filter {
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
        }
    }

    private data class ResultCache<T>(val deferredResult: Deferred<T>, val isUpToDate: Boolean)

    private abstract class BaseCache<T>(private val initialValue: T) {
        private var resultCache = ResultCache(CompletableDeferred(initialValue), false)
        private val lock = ReentrantLock()

        suspend fun getValue(): T {
            val resultCache = lock.withLock {
                if (resultCache.deferredResult.isCompleted && !resultCache.isUpToDate) {
                    resultCache = ResultCache(loadValue(), true)
                }
                resultCache
            }

            return resultCache.deferredResult.await()
        }

        fun markOutdated() {
            lock.withLock {
                resultCache = ResultCache(resultCache.deferredResult, false)
            }
        }

        protected fun clear() {
            lock.withLock {
                resultCache = ResultCache(CompletableDeferred(initialValue), true)
            }
        }

        abstract fun loadValue(): Deferred<T>
    }

    companion object {
        private val LOG = logger<JupyterKotlinProjectArtifactsService>()

        fun getInstance(project: Project): JupyterKotlinProjectArtifactsService {
            return project.service()
        }
    }
}