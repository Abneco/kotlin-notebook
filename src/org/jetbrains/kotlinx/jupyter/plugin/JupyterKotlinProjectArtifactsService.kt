// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.openapi.Disposable
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
import com.intellij.openapi.vfs.VirtualFile
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
import kotlinx.coroutines.runBlocking
import org.jetbrains.concurrency.asDeferred
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlinx.jupyter.plugin.actions.refactor.NotebookNotificationUtility
import org.jetbrains.kotlinx.jupyter.plugin.index.KotlinNotebookPermanentIndexService
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookPerFileSettingsCache
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookSettings
import org.jetbrains.kotlinx.jupyter.plugin.util.ProjectArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.util.isNotEmptyDirectory
import org.jetbrains.kotlinx.jupyter.plugin.util.parentsWithSelf
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


private enum class DependenciesState {
    PROVIDED,
    OUTDATED,
    ABSENT
}

private data class BuildResult(val artifacts: ProjectArtifacts, val state: DependenciesState) {
    companion object {
        val EMPTY = BuildResult(emptyList(), DependenciesState.PROVIDED)
    }
}

@Service(Service.Level.PROJECT)
class JupyterKotlinProjectArtifactsService(val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
    private val buildResultCache: ConcurrentHashMap<VirtualFile, BaseCache<BuildResult>> = ConcurrentHashMap()
    private val librariesCache: ConcurrentHashMap<VirtualFile, BaseCache<ProjectArtifacts>> = ConcurrentHashMap()

    private val sessionData = mutableMapOf<String, SessionData>()
    private val sessionDataLock = ReentrantLock()

    private var firstRun: Boolean = true

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
        addSessionListener()
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
                    buildResultCache.values.forEach { it.markOutdated() }
                    librariesCache.values.forEach { it.markOutdated() }
                }
            }
        }
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, listener)
    }

    private fun addSessionListener() {
        val sessionListener = object : JupyterRuntimeService.Listener {
            override fun sessionDeleted(session: JupyterNotebookSession) {
                sessionDataLock.withLock {
                    val (notebookFile, _) = sessionData.remove(session.sessionId) ?: return@withLock
                    if (sessionData.values.none { notebookFile.file == it.file.file }) {
                        buildResultCache.remove(notebookFile.file)
                        librariesCache.remove(notebookFile.file)
                    }
                }
            }
        }
        project.messageBus.connect(this).subscribe(JupyterRuntimeService.Listener.TOPIC, sessionListener)
    }

    private suspend fun buildProject(file: BackedNotebookVirtualFile, settings: KotlinNotebookSettings): BuildResult {
        if (!settings.isBuildProject) return BuildResult.EMPTY

        val cache = buildResultCache.computeIfAbsent(file.file) { BuildResultCache(project, this) }
        return cache.getValue()
    }

    private suspend fun getLibraries(file: BackedNotebookVirtualFile, settings: KotlinNotebookSettings): ProjectArtifacts {
        if (!settings.isAddProjectLibrariesToClasspath) return emptyList()

        val cache = librariesCache.computeIfAbsent(file.file) { LibrariesCache(project, coroutineScope) }
        return cache.getValue()
    }

    fun registerSession(session: JupyterNotebookSession) {
        val file = session.virtualFile ?: return
        sessionDataLock.withLock {
            sessionData[session.sessionId] = SessionData(file, mutableSetOf())
        }
    }

    fun getNewArtifactsForSession(sessionId: String): Collection<String> {
        val (notebookFile, oldArtifacts) = sessionDataLock.withLock { sessionData[sessionId] } ?: return emptyList()

        val notebookSettings = KotlinNotebookPerFileSettingsCache.getInstance(project).getSettings(notebookFile)

        val (buildProjectResult, libraries) = runBlocking {
            Pair(buildProject(notebookFile, notebookSettings), getLibraries(notebookFile, notebookSettings))
        }
        val allArtifacts = buildProjectResult.artifacts + libraries
        val newArtifacts = sessionDataLock.withLock {
            val newArtifacts = allArtifacts.filter { it !in oldArtifacts }
            oldArtifacts.addAll(newArtifacts)
            newArtifacts
        }

        when (buildProjectResult.state) {
            DependenciesState.OUTDATED -> NotebookNotificationUtility.showOutdatedDependencies(project)
            DependenciesState.ABSENT -> {
                NotebookNotificationUtility.showAbsentDependencies(project)
                if (firstRun) {
                    firstRun = false
                    throw RuntimeException(JupyterKotlinBundle.message("kotlin.jupyter.dependencies.build.error.throwable"))
                }
                return emptyList()
            }
            else -> {}
        }
        firstRun = false

        return newArtifacts
    }

    override fun dispose() = Unit

    private data class SessionData(val file: BackedNotebookVirtualFile, val artifactsCache: MutableSet<String>)

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

                val projectClasspath = allModules.flatMap {
                    ModuleRootManager.getInstance(it).orderEntries().withoutSdk().classes().pathsList.pathList
                }.distinct()

                val state = if (!buildResult.hasErrors()) DependenciesState.PROVIDED
                else if (projectClasspath.any { File(it).isNotEmptyDirectory }) DependenciesState.OUTDATED
                else DependenciesState.ABSENT

                BuildResult(projectClasspath, state)
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
                    it.name != KotlinNotebookPermanentIndexService.SCRIPT_DEPENDENCIES_LIBRARY_NAME
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

        suspend fun JupyterKotlinProjectArtifactsService.buildProjectAndGetLibraries(notebookFile: BackedNotebookVirtualFile): ProjectArtifacts {
            val settings = KotlinNotebookPerFileSettingsCache.getInstance(project).getSettings(notebookFile)
            return buildProject(notebookFile, settings).artifacts + getLibraries(notebookFile, settings)
        }
    }
}