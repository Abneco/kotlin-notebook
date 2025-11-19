// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.scriptingSupport.getSelectedSdkOrAnyAcceptable
import com.intellij.kotlin.jupyter.core.scriptingSupport.with
import com.intellij.kotlin.jupyter.core.util.debugInTests
import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrNull
import com.intellij.kotlin.jupyter.k2.project.model.findK2WorkspaceScriptEntities
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.projectStructure.analysisContextModule
import org.jetbrains.kotlin.idea.core.script.k2.asEntity
import org.jetbrains.kotlin.idea.core.script.k2.configurations.sdkId
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptConfigurationProviderExtension
import org.jetbrains.kotlin.idea.core.script.k2.modules.updateKotlinScriptEntities
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
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
class NotebookScriptConfigurationsManager(override val project: Project) : ScriptConfigurationProviderExtension {
    val workspaceModel: WorkspaceModel
        get() = project.workspaceModel

    val virtualFileUrlManager: VirtualFileUrlManager
        get() = project.workspaceModel.getVirtualFileUrlManager()

    /**
     * For now, we do not create it here as we have our own cycle of updates.
     * Notebook scheduler should control workspace model updates
     */
    override suspend fun createConfiguration(
        virtualFile: VirtualFile, definition: ScriptDefinition
    ): ScriptCompilationConfigurationResult? = null

    override fun getConfiguration(virtualFile: VirtualFile): ScriptCompilationConfigurationResult? = virtualFile.topLevelFile?.let {
        super.getConfiguration(it)
    }

    private val VirtualFile.topLevelFile: VirtualFile?
        get() {
            val topLevelFile = getTopLevelFileOrNull()
            if (topLevelFile == null) {
                notebookLogger().info("No top level file found for ${name}")
            }

            return topLevelFile
        }

    @OptIn(KaImplementationDetail::class)
    suspend fun updateConfigurations(scripts: Iterable<KotlinNotebookScriptModel>) {
        val sdkHomePath = getSelectedSdkOrAnyAcceptable(project)?.homePath
        if (sdkHomePath == null) {
            notebookLogger().warn("No JDK SDK is set for the project")
        }

        val configurations = scripts.associate { ktScript ->
            val virtualFile = ktScript.virtualFile
            virtualFile.analysisContextModule = null

            val configurationWrapper = ktScript.refinedConfiguration.with {
                if (sdkHomePath != null) {
                    jvm.jdkHome(File(sdkHomePath))
                }
            }.asSuccess()

            val topLevelFile = (virtualFile as VirtualFileWindow).delegate
            topLevelFile to configurationWrapper
        }

        updateWorkspaceModel(configurations)
    }

    suspend fun updateWorkspaceModel(resultPerFile: Map<VirtualFile, ScriptCompilationConfigurationResult>) {
        val tmp = MutableEntityStorage.create()

        for ((file, result) in resultPerFile) {
            tmp.addNotebookConfiguration(
                KotlinNotebookScriptModel(
                    file,
                    result.valueOrNull() ?: continue
                )
            )
        }

        project.updateKotlinScriptEntities(KotlinNotebookScriptEntitySource) { model -> // add new data, target only the base K2 script source
            model.replaceBySource({ it is KotlinNotebookScriptEntitySource }, tmp)
        }
    }

    suspend fun clearNotebookLibraryDependencies(notebookFile: BackedNotebookVirtualFile) {
        val workspaceSnapshot = workspaceModel.currentSnapshot
        val tmpSnapshot = MutableEntityStorage.from(workspaceSnapshot)

        val dependencies = notebookFile.findK2WorkspaceScriptEntities(workspaceModel).flatMap { it.dependencies }

        dependencies.forEach {
            it.resolve(workspaceSnapshot)?.let { libraryEntity ->
                tmpSnapshot.removeEntity(libraryEntity)
            }
        }

        // Could be clean with replaceBySource ({ it is NotebookEntitySource }, tmp)
        // where tmp contains only 1 script entity with default dependencies
        workspaceModel.update("Clearing Kotlin Notebook scripting modules for ${notebookFile.file.name}") { model ->
            model.applyChangesFrom(tmpSnapshot)
        }
    }

    private fun MutableEntityStorage.addNotebookConfiguration(
        notebookModuleConfiguration: KotlinNotebookScriptModel
    ) {
        fun buildLibraryDependencies(): Collection<KotlinScriptLibraryEntityId> {
            val dependencyViews = notebookModuleConfiguration.createConfigurationDependencyViews(project)
            return dependencyViews.flatMapTo(mutableSetOf()) {
                it.getOrUpdateLibraryDependencies(project, this)
            }
        }

        val virtualFile = notebookModuleConfiguration.virtualFile
        val libraryIds = buildLibraryDependencies().toList()

        notebookLogger().debugInTests {
            "Updating scripting module for notebook '${virtualFile.nameWithoutExtension}' with libraries: $libraryIds"
        }

        this addEntity KotlinScriptEntity(
            virtualFile.toVirtualFileUrl(virtualFileUrlManager), libraryIds,
            KotlinNotebookScriptEntitySource
        ) {
            configuration = notebookModuleConfiguration.refinedConfiguration.configuration?.asEntity()
            sdkId = notebookModuleConfiguration.refinedConfiguration.configuration?.sdkId
        }
    }

    companion object {
        fun getInstance(project: Project): NotebookScriptConfigurationsManager = project.service()
    }
}
