// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.scriptingSupport.getSelectedSdkOrAnyAcceptable
import com.intellij.kotlin.jupyter.core.scriptingSupport.with
import com.intellij.kotlin.jupyter.core.util.debugInTests
import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrNull
import com.intellij.kotlin.jupyter.k2.project.model.findK2WorkspaceScriptEntities
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.analysisContextModule
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptRefinedConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptWorkspaceModelManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.asSuccess
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
class NotebookScriptConfigurationsManager(val project: Project) : ScriptRefinedConfigurationResolver, ScriptWorkspaceModelManager {
    val cache: ConcurrentHashMap<VirtualFile, ScriptCompilationConfigurationResult> =
        ConcurrentHashMap<VirtualFile, ScriptCompilationConfigurationResult>()
    val workspaceModel: WorkspaceModel
        get() = project.workspaceModel

    val virtualFileUrlManager: VirtualFileUrlManager
        get() = project.workspaceModel.getVirtualFileUrlManager()

    /**
     * For now, we do not create it here
     * as we have our own cycle of updates.
     */
    override suspend fun create(
        virtualFile: VirtualFile, definition: ScriptDefinition
    ): ScriptCompilationConfigurationResult? = get(virtualFile)

    /**
     * Depending on a [VirtualFileWindow] is dangerous as it might get invalidated soon after it was processed.
     * For this end, one should associate configuration with top level [VirtualFile].
     */
    override fun get(virtualFile: VirtualFile): ScriptCompilationConfigurationResult? {
        val topLevelFile = virtualFile.getTopLevelFileOrNull()

        if (topLevelFile == null) { // We may get there in the case of a light file we usually get as an intermediate result
            // of some refactorings / intention previews
            notebookLogger().info("No top level file found for ${virtualFile.name}")
            return null
        }

        val configuration = cache[topLevelFile]

        return if (cache.isEmpty()) {
            JupyterCompilerService.getInstance(project).getDefaultConfiguration(topLevelFile)
        } else {
            if (configuration == null) {
                notebookLogger().warn("No configuration found for ${topLevelFile.name}")
            }
            configuration
        }
    }

    @OptIn(KaImplementationDetail::class)
    fun updateConfigurations(scripts: Iterable<KotlinNotebookScriptModel>) {
        val sdkHomePath = getSelectedSdkOrAnyAcceptable(project)?.homePath
        if (sdkHomePath == null) {
            notebookLogger().warn("No JDK SDK is set for the project")
        }

        val configurations = scripts.associate { ktScript ->
            val virtualFile = ktScript.virtualFile
            virtualFile.analysisContextModule = null

            val configurationWrapper = ktScript.refinedConfigurationResult.with {
                if (sdkHomePath != null) {
                    jvm.jdkHome(File(sdkHomePath))
                }
            }.asSuccess()

            val topLevelFile = (virtualFile as VirtualFileWindow).delegate
            topLevelFile to configurationWrapper
        }

        cache.putAll(configurations)
    }

    /**
     * Since we already detected that there is no configuration provided,
     * one needs to set up any existing context module to be analyzed for the smooth analysis.
     * Note, it's important to invalidate this key as soon as possible.
     * It happens in [updateConfigurations].
     */
    @OptIn(KaImplementationDetail::class)
    @RequiresReadLock
    private fun VirtualFile.setUpTemporaryModuleForAnalysis(donorInjectedFile: VirtualFile) {
        val randomKtFile = donorInjectedFile.findPsiFile(project) as? KtFile ?: return
        val randomModule = KaModuleProvider.getInstance(project).getModule(randomKtFile, null)

        this.analysisContextModule = randomModule
    }

    override suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptCompilationConfigurationResult>) {
        val tmp = MutableEntityStorage.create()

        val configurationsByNotebook = cache.toConfigurationInfoPerNotebook()
        creteOrUpdateScriptModules(configurationsByNotebook, tmp)

        project.workspaceModel.update("Updating Kotlin Notebook scripting modules") { model -> // add new data, target only the base K2 script source
            model.replaceBySource({ it is KotlinNotebookScriptEntitySource }, tmp)
        }
    }

    // this might be parallel
    private suspend fun creteOrUpdateScriptModules(
        configurationsPerNotebook: Map<VirtualFile, KotlinNotebookScriptsModuleConfigurationInfo>,
        mutableEntityStorage: MutableEntityStorage
    ) {
        for ((_, moduleConfigurations) in configurationsPerNotebook) {
            updateNotebookConfiguration(project, mutableEntityStorage, moduleConfigurations)
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

        workspaceModel.update("Clearing Kotlin Notebook scripting modules for ${notebookFile.file.name}") { model ->
            model.applyChangesFrom(tmpSnapshot)
        }
    }

    private fun updateNotebookConfiguration(
        project: Project,
        mutableEntityStorage: MutableEntityStorage,
        notebookModuleConfiguration: KotlinNotebookScriptsModuleConfigurationInfo
    ) {
        fun buildLibraryDependencies(): Collection<KotlinScriptLibraryEntityId> {
            val dependencyViews = notebookModuleConfiguration.createConfigurationDependencyViews(project)
            return dependencyViews.flatMapTo(mutableSetOf()) {
                it.getOrUpdateLibraryDependencies(project, mutableEntityStorage)
            }
        }

        val virtualFile = notebookModuleConfiguration.notebookFile
        val libraryIds = buildLibraryDependencies().toList()

        notebookLogger().debugInTests {
            "Updating scripting module for notebook '${virtualFile.nameWithoutExtension}' with libraries: $libraryIds"
        }

        mutableEntityStorage addEntity KotlinScriptEntity(
            virtualFile.toVirtualFileUrl(virtualFileUrlManager), libraryIds,
            KotlinNotebookScriptEntitySource
        ) {
            this.sdkId = notebookModuleConfiguration.sdkId
        }
    }

    companion object {
        fun getInstance(project: Project): NotebookScriptConfigurationsManager = project.service()
    }
}
