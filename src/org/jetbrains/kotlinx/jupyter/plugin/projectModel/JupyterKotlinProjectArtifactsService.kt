// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.projectModel

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.JavaSourceRootPropertiesEntity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import com.intellij.util.cancelOnDispose
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.asDeferred
import org.jetbrains.kotlinx.jupyter.plugin.notifications.notebookNotifications
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookDependencies
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookPerFileSettingsCache
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookSettings
import org.jetbrains.kotlinx.jupyter.plugin.settings.findLibraries
import org.jetbrains.kotlinx.jupyter.plugin.settings.findModules
import org.jetbrains.kotlinx.jupyter.plugin.settings.isAffectedBy
import org.jetbrains.kotlinx.jupyter.plugin.settings.isEmpty
import org.jetbrains.kotlinx.jupyter.plugin.util.ProjectArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.util.isNotEmptyDirectory
import org.jetbrains.kotlinx.jupyter.plugin.util.parentsWithSelf
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.core.impl.file.getOriginalVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSession
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterNotebookSessionId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.notebook.JupyterRuntimeService
import java.io.File
import java.util.concurrent.ConcurrentHashMap


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
    private val sessionData = ConcurrentHashMap<JupyterNotebookSessionId, SessionData>()

    private val sourceFileExtensionsOfInterest = setOf("kt", "java")

    init {
        addBuildListener()
        addVFSChangesListener()
        addSessionListener()
        addProjectStructureListeners()
    }

    private fun addProjectStructureListeners() {
        coroutineScope.launch(Dispatchers.Default) {
            WorkspaceModel.getInstance(project).eventLog.collect { event ->
                handleWorkspaceModelChange(event)
            }
        }
        LibraryTablesRegistrar.getInstance().getLibraryTable(project).addListener(object : LibraryTable.Listener {
            override fun afterLibraryAdded(newLibrary: Library) = invalidateLibrariesCaches(newLibrary)
            override fun afterLibraryRemoved(library: Library) = invalidateLibrariesCaches(library)
        }, this)
    }

    private fun handleWorkspaceModelChange(event: VersionedStorageChange) {
        val changedModules = buildSet<Module> {
            for (isBefore in listOf(true, false)) {
                val moduleEntities = event.getChangedEntities(JavaSourceRootPropertiesEntity::class.java, isBefore).map {
                    it.sourceRoot.contentRoot.module
                } + event.getChangedEntities(JavaModuleSettingsEntity::class.java, isBefore).map {
                    it.module
                } + event.getChangedEntities(ModuleEntity::class.java, isBefore)

                val storage = if (isBefore) event.storageBefore else event.storageAfter
                addAll(moduleEntities.mapNotNull { it.findModule(storage) })
            }
        }

        invalidateBuildResultCaches(changedModules)
    }

    private fun <T : WorkspaceEntity> VersionedStorageChange.getChangedEntities(
        entityClass: Class<T>,
        isBefore: Boolean
    ): Collection<T> {
        return getChanges(entityClass).mapNotNull { if (isBefore) it.oldEntity else it.newEntity }
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
            private fun isFileOfInterest(file: VirtualFile): Boolean {
                val fileProjects = ProjectLocator.getInstance().getProjectsForFile(file)
                if (project !in fileProjects) return false

                // TODO: reconsider this approach, maybe create extra option
                if (file.parentsWithSelf.any { it.isDirectory && it.name == "generated" }) return false
                return file.extension in sourceFileExtensionsOfInterest
            }

            override fun after(events: List<VFileEvent>) {
                val affectedFiles = events.mapNotNull { it.file }.filter(::isFileOfInterest)
                if (affectedFiles.isEmpty()) return

                val changedModules = affectedFiles.mapNotNull {
                    ModuleUtilCore.findModuleForFile(it, project)
                }

                invalidateBuildResultCaches(changedModules)
            }
        }
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, listener)
    }

    private fun invalidateCaches(
        settingGetter: KotlinNotebookSettings.() -> KotlinNotebookDependencies,
        changedDependencies: KotlinNotebookDependencies
    ) {
        if (changedDependencies == KotlinNotebookDependencies.None) return
        val fileSettingsCache = KotlinNotebookPerFileSettingsCache.getInstance(project)
        sessionData.values.map { it.file.file }.forEach { file ->
            val settings = fileSettingsCache.getCachedSettings(file)
            if (settings == null || settings.settingGetter().isAffectedBy(changedDependencies)) {
                coroutineScope.launch(Dispatchers.EDT) {
                    project.service<JupyterKotlinOutdatedDependenciesNotificationService>()
                        .notify(NotebookId(file.getOriginalVirtualFile()))
                }
            }
        }
    }

    private fun invalidateLibrariesCaches(changedLibrary: Library) {
        coroutineScope.async {
            invalidateCaches(
                KotlinNotebookSettings::projectLibraries,
                KotlinNotebookDependencies.fromLibraries(listOf(changedLibrary))
            )
        }
    }

    private fun invalidateBuildResultCaches(changedModules: Collection<Module>) {
        val allAffectedModules = buildSet {
            addAll(changedModules)
            changedModules.forEach { ModuleUtilCore.collectModulesDependsOn(it, this@buildSet) }
        }
        invalidateCaches(
            KotlinNotebookSettings::projectDependencies,
            KotlinNotebookDependencies.fromModules(allAffectedModules)
        )
    }

    private fun addSessionListener() {
        val sessionListener = object : JupyterRuntimeService.Listener {
            override fun sessionDeleted(session: JupyterNotebookSession) {
                sessionData.remove(session.sessionId)
                session.virtualFile?.let {
                    project.service<JupyterKotlinOutdatedDependenciesNotificationService>().notificationExpire(NotebookId(it.originFile))
                }
            }
        }
        project.messageBus.connect(this).subscribe(JupyterRuntimeService.Listener.TOPIC, sessionListener)
    }

    private suspend fun buildProject(settings: KotlinNotebookSettings): BuildResult {
        if (settings.projectDependencies.isEmpty()) return BuildResult.EMPTY
        return withContext(Dispatchers.Default) {
            buildModules(settings.projectDependencies.findModules(project))
        }
    }

    private fun getLibraries(settings: KotlinNotebookSettings): ProjectArtifacts {
        if (settings.projectLibraries.isEmpty()) return emptyList()
        return if (settings.projectLibraries.isEmpty()) emptyList() else settings.projectLibraries.findLibraries(project)
            .flatMap { library ->
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

    fun registerSession(session: JupyterNotebookSession) {
        val file = session.virtualFile ?: return
        sessionData[session.sessionId] = SessionData(
            file = file,
            artifacts = coroutineScope.async(start = CoroutineStart.LAZY) {
                val notebookSettings = KotlinNotebookPerFileSettingsCache.getInstance(project).getSettings(file)
                Pair(buildProject(notebookSettings), getLibraries(notebookSettings))
            },
        )
    }

    fun getNewArtifactsForSession(sessionId: JupyterNotebookSessionId): Collection<String> {
        val session = sessionData[sessionId] ?: return emptyList()
        if (session.alreadyReturnedArtifacts) return emptyList()
        session.alreadyReturnedArtifacts = true

        val (buildProjectResult, libraries) = runBlocking { session.artifacts.await() }

        when (buildProjectResult.state) {
            DependenciesState.OUTDATED ->
                project.notebookNotifications.showOutdatedDependencies()
            DependenciesState.ABSENT -> {
                project.notebookNotifications.showAbsentDependencies()
                return emptyList()
            }
            else -> {}
        }

        return buildProjectResult.artifacts + libraries
    }

    override fun dispose() = Unit

    private data class SessionData(
        val file: BackedNotebookVirtualFile,
        val artifacts: Deferred<Pair<BuildResult, ProjectArtifacts>>,
        var alreadyReturnedArtifacts: Boolean = false,
    )

    private suspend fun buildModules(modules: Collection<Module>): BuildResult {
        if (modules.isEmpty()) return BuildResult.EMPTY

        val taskManager = ProjectTaskManager.getInstance(project)
        val buildTask = taskManager.createModulesBuildTask(modules.toTypedArray(), true, true, false)
        val buildTaskContext = ProjectTaskContext().apply {
            enableCollectionOfGeneratedFiles()
        }

        val buildResultDeferred = taskManager.run(buildTaskContext, buildTask).asDeferred()
        buildResultDeferred.cancelOnDispose(this)

        val allModules = getDependencies(modules)
        val projectClasspath = allModules.flatMap {
            ModuleRootManager.getInstance(it).orderEntries().withoutSdk().classes().pathsList.pathList
        }.distinct()

        val buildResult = buildResultDeferred.await()
        val state = if (!buildResult.hasErrors()) DependenciesState.PROVIDED
        else if (projectClasspath.any { File(it).isNotEmptyDirectory }) DependenciesState.OUTDATED
        else DependenciesState.ABSENT

        return BuildResult(projectClasspath, state)
    }

    private fun getDependencies(modules: Collection<Module>): Array<Module> {
        val result = mutableSetOf<Module>()
        result.addAll(modules)
        for (m in modules) ModuleUtilCore.getDependencies(m, result)
        return result.toTypedArray()
    }

    companion object {
        private val LOG = logger<JupyterKotlinProjectArtifactsService>()

        fun getInstance(project: Project): JupyterKotlinProjectArtifactsService {
            return project.service()
        }

        suspend fun JupyterKotlinProjectArtifactsService.buildProjectAndGetLibraries(notebookFile: BackedNotebookVirtualFile): ProjectArtifacts {
            sessionData.values.firstOrNull { it.file == notebookFile }?.artifacts
                ?.await()?.let { (project, libraries) ->
                    return project.artifacts + libraries
                }

            val settings = KotlinNotebookPerFileSettingsCache.getInstance(project).getSettings(notebookFile)
            return buildProject(settings).artifacts + getLibraries(settings)
        }
    }
}