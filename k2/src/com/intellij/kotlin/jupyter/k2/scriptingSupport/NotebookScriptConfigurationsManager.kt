// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.settings.NotebookProjectJdkOption
import com.intellij.kotlin.jupyter.core.util.debugInTests
import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrNull
import com.intellij.kotlin.jupyter.k2.project.model.filterBoundToOneModule
import com.intellij.kotlin.jupyter.k2.project.model.findK2WorkspaceModule
import com.intellij.kotlin.jupyter.k2.project.model.notebookScriptLibrariesEntities
import com.intellij.kotlin.jupyter.k2.project.model.toK2RuntimeModuleName
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.sourceRoots
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.analysisContextModule
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptRefinedConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptWorkspaceModelManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ifEmpty
import kotlin.script.experimental.api.asSuccess

/**
 * Special marker used to distinguish Kotlin Notebook-related entities
 */
class KotlinNotebookScriptEntitySource(virtualFileUrl: VirtualFileUrl) : KotlinScriptEntitySource(virtualFileUrl)

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
    val cache: ConcurrentHashMap<VirtualFile, ScriptConfigurationWithSdk> = ConcurrentHashMap<VirtualFile, ScriptConfigurationWithSdk>()

    /**
     * For now, we do not create it here
     * as we have our own cycle of updates.
     */
    override suspend fun create(
        virtualFile: VirtualFile,
        definition: ScriptDefinition
    ): ScriptConfigurationWithSdk? = get(virtualFile)

    /**
     * Depending on a [VirtualFileWindow] is dangerous as it might get invalidated soon after it was processed.
     * For this end, one should associate configuration with top level [VirtualFile].
     */
    override fun get(virtualFile: VirtualFile): ScriptConfigurationWithSdk? {
        val topLevelFile = virtualFile.getTopLevelFileOrNull()

        if (topLevelFile == null) {
            // We may get there in the case of a light file we usually get as an intermediate result
            // of some refactorings / intention previews
            notebookLogger().info("No top level file found for ${virtualFile.name}")
            return null
        }

        val configuration = cache[topLevelFile]

        return if (cache.isEmpty()) {
            getDefaultConfiguration(topLevelFile)
        } else {
            if (configuration == null) {
                notebookLogger().warn("No configuration found for ${topLevelFile.name}")
            }
            configuration
        }
    }

    fun getDefaultConfiguration(virtualFile: VirtualFile): ScriptConfigurationWithSdk? {
        val configuration = JupyterCompilerService.getInstance(project).getDefaultConfiguration(virtualFile) ?: return null

        return ScriptConfigurationWithSdk(configuration, getSelectedSdkOrAnyAcceptable())
    }

    private fun Sdk.canBeUsedForScript(): Boolean {
        if (sdkType !is JavaSdkType) return false
        val rootClasses = rootProvider.getFiles(OrderRootType.CLASSES)
        return rootClasses.isNotEmpty() && rootClasses.all { it.isValid }
    }

    private fun getSelectedSdkOrAnyAcceptable(): Sdk? {
        val registeredJdks = ProjectJdkTable.getInstance().allJdks.toSet().ifEmpty {
            return null
        }
        return NotebookProjectJdkOption.suggestJdks(project).firstOrNull {
            it.canBeUsedForScript() && it in registeredJdks
        }
    }

    @OptIn(KaImplementationDetail::class)
    fun updateConfigurations(scripts: Iterable<KotlinNotebookScriptModel>) {
        val sdk = getSelectedSdkOrAnyAcceptable()
        if (sdk == null) {
            notebookLogger().warn("No JDK SDK is set for the project")
        }

        val configurations = scripts.associate { ktScript ->
            val virtualFile = ktScript.virtualFile
            virtualFile.analysisContextModule = null
            val configuration = ktScript.refinedConfigurationResult.asSuccess()

            val topLevelFile = (virtualFile as VirtualFileWindow).delegate
            topLevelFile to ScriptConfigurationWithSdk(configuration, sdk)
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

    override suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptConfigurationWithSdk>) {
        val workspaceSnapshot = project.workspaceModel.currentSnapshot
        val tmp = MutableEntityStorage.from(workspaceSnapshot)

        val configurationsByNotebook = cache.toConfigurationInfoPerNotebook()
        creteOrUpdateScriptModules(configurationsByNotebook, tmp)

        project.workspaceModel.update("Updating Kotlin Notebook scripting modules") { model ->
            // add new data, target only the base K2 script source
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
        val workspaceModel = project.workspaceModel
        val workspaceSnapshot = workspaceModel.currentSnapshot
        val tmpSnapshot = MutableEntityStorage.from(workspaceSnapshot)

        val libraryEntities = notebookFile.notebookScriptLibrariesEntities(project, workspaceSnapshot)
        if (libraryEntities.isEmpty()) return

        val notebookModule = notebookFile
            .findK2WorkspaceModule(project)
            ?.findModuleEntity(tmpSnapshot) ?: return
        val relatedToThisModule = libraryEntities
            .filterBoundToOneModule(workspaceModel.currentSnapshot, notebookModule)

        relatedToThisModule.forEach { libraryEntity ->
            tmpSnapshot.removeEntity(libraryEntity)
        }
        workspaceModel.update("Clearing Kotlin Notebook scripting modules") { model ->
            model.applyChangesFrom(tmpSnapshot)
        }
    }

    private fun updateNotebookConfiguration(
        project: Project,
        mutableEntityStorage: MutableEntityStorage,
        notebookModuleConfiguration: KotlinNotebookScriptsModuleConfigurationInfo
    ) {
        fun buildLibraryDependencies(): Collection<LibraryDependency> {
            val dependencyViews = notebookModuleConfiguration.createConfigurationDependencyViews()

            return buildSet {
                for (view in dependencyViews) {
                    val libraryDependencies = view.getOrUpdateLibraryDependencies(project, mutableEntityStorage)

                    addAll(libraryDependencies)
                }
            }
        }

        val moduleName = notebookModuleConfiguration.notebookFile.toK2RuntimeModuleName(project)

        val sdkDependency =
            notebookModuleConfiguration.sdkInfo
                ?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

        val source = KotlinNotebookScriptEntitySource(
            notebookModuleConfiguration.notebookFile.toVirtualFileUrl(
                WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
            )
        )

        val libraryDependencies = buildLibraryDependencies()
        val dependencies = libraryDependencies + listOfNotNull(sdkDependency)

        notebookLogger().debugInTests {
            "Updating scripting module for notebook '${notebookModuleConfiguration.notebookFile.nameWithoutExtension}' with libraries: $libraryDependencies"
        }

        val newEntry = ModuleEntity(moduleName, dependencies, source)

        val oldEntry = mutableEntityStorage.resolve(ModuleId(moduleName))
        if (oldEntry != null) {
            mutableEntityStorage.modifyModuleEntity(oldEntry) {
                this.dependencies = newEntry.dependencies
                this.sourceRoots = newEntry.sourceRoots
                this.name = newEntry.name
            }
            return
        }

        // seen firstly
        mutableEntityStorage.addEntity(newEntry)
    }

    companion object {
        const val NOTEBOOK_MODULE_NAME_PREFIX: String = "$KOTLIN_SCRIPTS_MODULE_NAME.Kotlin Notebooks"

        fun getInstance(project: Project): NotebookScriptConfigurationsManager = project.service()
    }
}