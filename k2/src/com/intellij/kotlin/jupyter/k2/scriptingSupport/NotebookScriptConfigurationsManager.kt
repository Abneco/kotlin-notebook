// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.notifications.notebookNotifications
import com.intellij.kotlin.jupyter.core.scriptingSupport.getSelectedSdkOrAnyAcceptable
import com.intellij.kotlin.jupyter.core.scriptingSupport.with
import com.intellij.kotlin.jupyter.core.util.ConsecutiveAttemptsGuard
import com.intellij.kotlin.jupyter.core.util.debugInTests
import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrNull
import com.intellij.kotlin.jupyter.k2.project.model.addOrUpdateLibraryEntity
import com.intellij.kotlin.jupyter.k2.project.model.findK2WorkspaceScriptEntities
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.idea.core.script.k2.configurations.sdkId
import org.jetbrains.kotlin.idea.core.script.k2.getOrCreateScriptConfigurationId
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
import org.jetbrains.kotlin.idea.core.script.k2.modules.modifyKotlinScriptLibraryEntity
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.utils.mapToSetOrEmpty
import java.io.File
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

/**
 * Special marker used to distinguish Kotlin Notebook-related entities
 */
object KotlinNotebookScriptEntitySource : EntitySource

/**
 * K2 entry point that manages script dependencies for Kotlin notebooks within a given project.
 *
 * Basically, it's the replacement for [org.jetbrains.kotlin.idea.core.script.k1.configuration.ScriptingSupport] in K2 mode.
 *
 * Update handles steps:
 *  - preparation of script configurations
 *  - updating internal modules for scripts and its dependency as libraries.
 *
 *  Note that now for each [BackedNotebookVirtualFile] a separate module is created, and for module there are its own dependencies.
 */
@Service(Service.Level.PROJECT)
class NotebookScriptConfigurationsManager(val project: Project) {
    private val workspaceModel: WorkspaceModel
        get() = project.workspaceModel

    private val VirtualFile.virtualFileUrl: VirtualFileUrl
        get() = toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())

    private suspend fun Project.updateKotlinScriptEntities(entitySource: EntitySource, updater: (MutableEntityStorage) -> Unit) {
        workspaceModel.update("updating kotlin script entities [$entitySource]") { updater(it) }
    }

    private val updaterAttemptsGuard = ConsecutiveAttemptsGuard(
        WORKSPACE_MODEL_UPDATE_ATTEMPTS_THRESHOLD,
        onThresholdReached = { project.notebookNotifications.showScriptingUpdateFailed() },
    ) { failureCount, e ->
        LOG.warn("Workspace model update failed (consecutive failures: $failureCount)", e)
    }

    fun getKotlinScriptEntity(virtualFile: VirtualFile): KotlinScriptEntity? = virtualFile.topLevelFile?.let {
        workspaceModel.currentSnapshot.getVirtualFileUrlIndex()
            .findEntitiesByUrl(it.virtualFileUrl)
            .filterIsInstance<KotlinScriptEntity>().singleOrNull()
    }

    private val VirtualFile.topLevelFile: VirtualFile?
        get() {
            val topLevelFile = getTopLevelFileOrNull()
            if (topLevelFile == null) {
                LOG.info("No top level file found for ${name}")
            }

            return topLevelFile
        }

    @OptIn(KaImplementationDetail::class)
    suspend fun updateConfigurations(scripts: Iterable<KotlinNotebookScriptModel>) {
        val sdkHomePath = getSelectedSdkOrAnyAcceptable(project)?.homePath
        if (sdkHomePath == null) {
            LOG.warn("No JDK SDK is set for the project")
        }

        val configurations = buildMap<VirtualFile, ScriptCompilationConfigurationResult> {
            for (scriptModel in scripts) {
                val virtualFile = scriptModel.virtualFile
                val topLevelFile = virtualFile.topLevelFile ?: continue

                val configurationWrapper = scriptModel.refinedConfiguration.with {
                    if (sdkHomePath != null) {
                        jvm.jdkHome(File(sdkHomePath))
                    }
                }.asSuccess()

                put(topLevelFile, configurationWrapper)
            }
        }

        updateWorkspaceModel(configurations)
    }

    suspend fun updateWorkspaceModel(resultPerFile: Map<VirtualFile, ScriptCompilationConfigurationResult>) {
        updaterAttemptsGuard.withConsecutiveAttempts {
            project.updateKotlinScriptEntities(KotlinNotebookScriptEntitySource) { model ->
                val tmp = MutableEntityStorage.create()
                val updatedFilesUrls = resultPerFile.keys.mapToSetOrEmpty {
                    it.virtualFileUrl
                }

                for ((file, result) in resultPerFile) {
                    addNotebookConfiguration(
                        tmp,
                        KotlinNotebookScriptModel(
                            file,
                            result.valueOrNull() ?: continue
                        )
                    )
                }

                tmp.addUnchangedNotebookEntities(model, updatedFilesUrls)
                model.replaceBySource({ it is KotlinNotebookScriptEntitySource }, tmp)
            }
        }
    }

    suspend fun clearNotebookLibraryDependencies(vararg notebookFiles: BackedNotebookVirtualFile) {
        val fileUrlManager = workspaceModel.getVirtualFileUrlManager()
        val notebookUrls = notebookFiles.mapTo(mutableSetOf()) {
            it.file.toVirtualFileUrl(fileUrlManager)
        }

        val dependencies = notebookFiles.flatMap { notebookFile ->
            notebookFile.findK2WorkspaceScriptEntities(workspaceModel).flatMap { it.dependencies }
        }

        val fileNames = notebookFiles.joinToString { it.file.name }
        workspaceModel.update("Clearing Kotlin Notebook scripting modules for $fileNames") { model ->
            for (depId in dependencies) {
                val libraryEntity = depId.resolve(model) ?: continue
                val remainingScripts = libraryEntity.usedInScripts - notebookUrls
                if (remainingScripts.isEmpty()) {
                    model.removeEntity(libraryEntity)
                } else {
                    model.modifyKotlinScriptLibraryEntity(libraryEntity) {
                        this.usedInScripts = remainingScripts.toMutableSet()
                    }
                }
            }
        }
    }

    suspend fun clearAllNotebookEntities() {
        updaterAttemptsGuard.resetAttempts()
        val emptyStorage = MutableEntityStorage.create()
        project.updateKotlinScriptEntities(KotlinNotebookScriptEntitySource) { model ->
            model.replaceBySource({ it is KotlinNotebookScriptEntitySource }, emptyStorage)
        }
    }

    /**
     * Puts in tmp snapshot all the present notebooks models,
     * except for ones being updates.
     * So that replaceBySource won't remove a part which is updated already.
     */
    private fun MutableEntityStorage.addUnchangedNotebookEntities(
        currentSnapshot: MutableEntityStorage,
        filesToUpdate: Set<VirtualFileUrl>
    ) {
        fun copyDependencies(from: KotlinScriptEntity) {
            val deps = from.dependencies.mapNotNull { it.resolve(currentSnapshot) }
            for (lib in deps) {
                addOrUpdateLibraryEntity(
                    lib.scope,
                    lib.classes,
                    lib.sources,
                    usedInScripts = lib.usedInScripts
                )
            }
        }

        val existingNotebooksEntities = currentSnapshot.entitiesBySource { it is KotlinNotebookScriptEntitySource }
            .filterIsInstance<KotlinScriptEntity>()
            .filter {
                !filesToUpdate.contains(it.virtualFileUrl)
            }

        LOG.debugInTests {
            val fileNamesBeingUpdated = filesToUpdate.joinToString { it.fileName }
            val existingEntitiesNames = existingNotebooksEntities.joinToString { it.virtualFileUrl.virtualFile?.name ?: "" }
            "Adding additional ${existingNotebooksEntities.count()} entities from: $existingEntitiesNames for update besides $fileNamesBeingUpdated"
        }

        for (model in existingNotebooksEntities) {
            // replaceBySource removes entities not present in target, so we must ensure referenced libraries exist
            copyDependencies(model)

            this addEntity KotlinScriptEntity(
                model.virtualFileUrl,
                model.dependencies,
                KotlinNotebookScriptEntitySource
            ) {
                configurationId = model.configurationId
                sdkId = model.sdkId
            }
        }
    }

    private fun addNotebookConfiguration(
        storage: MutableEntityStorage,
        notebookModuleConfiguration: KotlinNotebookScriptModel
    ) {
        fun buildLibraryDependencies(): Collection<KotlinScriptLibraryEntityId> {
            val dependencyViews = notebookModuleConfiguration.createConfigurationDependencyViews(project)
            return dependencyViews.flatMapTo(mutableSetOf()) {
                it.getOrUpdateLibraryDependencies(project, storage)
            }
        }

        val virtualFile = notebookModuleConfiguration.virtualFile
        val libraryIds = buildLibraryDependencies().toList()

        LOG.debugInTests {
            "Updating scripting module for notebook '${virtualFile.nameWithoutExtension}' with libraries: $libraryIds"
        }

        storage addEntity KotlinScriptEntity(
            virtualFile.virtualFileUrl, libraryIds,
            KotlinNotebookScriptEntitySource
        ) {
            configurationId = notebookModuleConfiguration.refinedConfiguration.configuration?.getOrCreateScriptConfigurationId(
                storage,
                KotlinNotebookScriptEntitySource)
            sdkId = notebookModuleConfiguration.refinedConfiguration.configuration?.sdkId
        }
    }

    companion object {
        private val LOG = notebookLogger()

        private const val WORKSPACE_MODEL_UPDATE_ATTEMPTS_THRESHOLD = 5

        fun getInstance(project: Project): NotebookScriptConfigurationsManager = project.service()
    }
}
