// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.projectModel.resolveLibraryDependencies
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.settings.ProjectJdkOption
import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrNull
import com.intellij.kotlin.jupyter.core.util.toBackedNotebookFile
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.jps.entities.modifyLibraryEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.sourceRoots
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.analysisContextModule
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.configurations.ScriptRefinedConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.k2.configurations.ScriptWorkspaceModelManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.asSuccess

/**
 * Special marker used to distinguish Kotlin Notebook-related entities
 */
class KotlinNotebookScriptEntitySource(virtualFileUrl: VirtualFileUrl) : KotlinScriptEntitySource(virtualFileUrl)

/**
 * K2 entry point that manages script dependencies for Kotlin notebooks within a given project.
 *
 * Basically, it's the replacement for [org.jetbrains.kotlin.idea.core.script.configuration.ScriptingSupport] in K2 mode.
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
        if (cache.isEmpty()) {
            return getDefaultConfiguration(topLevelFile)
        }
        if (configuration == null) {
            notebookLogger().warn("No configuration found for ${topLevelFile.name}")
        }

        return configuration
    }

    fun getDefaultConfiguration(virtualFile: VirtualFile): ScriptConfigurationWithSdk? {
        val configuration = JupyterCompilerService.getInstance(project).getDefaultConfiguration(virtualFile) ?: return null

        return ScriptConfigurationWithSdk(configuration, getScriptDefaultSdk())
    }

    private fun getScriptDefaultSdk(): Sdk? {
        val projectSdk = ProjectRootManager.getInstance(project).projectSdk?.takeIf { it.canBeUsedForScript() }
        if (projectSdk != null) return projectSdk

        val allJdks = ProjectJdkTable.getInstance().allJdks

        val anyJavaSdk = allJdks.find { it.canBeUsedForScript() }
        if (anyJavaSdk != null) {
            return anyJavaSdk
        }

        return null
    }

    private fun Sdk.canBeUsedForScript() = sdkType is JavaSdkType && hasValidClassPathRoots()

    private fun Sdk.hasValidClassPathRoots(): Boolean {
        val rootClasses = rootProvider.getFiles(OrderRootType.CLASSES)
        return rootClasses.isNotEmpty() && rootClasses.all { it.isValid }
    }

    @OptIn(KaImplementationDetail::class)
    fun updateConfigurations(scripts: Iterable<KotlinNotebookScriptModel>) {
        val sdk = ProjectJdkOption.getSdk(project) ?: ProjectJdkTable.getInstance().allJdks.firstOrNull()
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

    private suspend fun creteOrUpdateScriptModules(
        configurationsPerNotebook: Map<VirtualFile, KotlinNotebookScriptsModuleConfigurationInfo>,
        mutableEntityStorage: MutableEntityStorage
    ) {
        val virtualFileManager = project.serviceAsync<WorkspaceModel>().getVirtualFileUrlManager()

        for ((notebookFile, moduleConfigurations) in configurationsPerNotebook) {
            val notebookRuntimeDependencies = virtualFileManager.getNotebookDependenciesAsLibraryEntity(
                mutableEntityStorage,
                notebookFile,
                project,
                moduleConfigurations.configuration
            )

            updateNotebookConfiguration(project, mutableEntityStorage, moduleConfigurations, notebookRuntimeDependencies)
        }
    }

    suspend fun clearNotebookLibraryDependencies(notebookFile: BackedNotebookVirtualFile) {
        val workspaceModel = project.workspaceModel
        val workspaceSnapshot = workspaceModel.currentSnapshot
        val tmpSnapshot = MutableEntityStorage.from(workspaceSnapshot)

        val libraryEntity = tmpSnapshot.resolveLibraryDependencies(
            notebookFile.file.toK2RuntimeDependencyLibraryName(project),
            LibraryTableId.ProjectLibraryTableId
        )
        if (libraryEntity == null) return

        tmpSnapshot.modifyLibraryEntity(libraryEntity) {
            this.roots = mutableListOf()
        }
        workspaceModel.update("Clearing Kotlin Notebook scripting modules") { model ->
            model.applyChangesFrom(tmpSnapshot)
        }
    }

    private fun updateNotebookConfiguration(
        project: Project,
        mutableEntityStorage: MutableEntityStorage,
        notebookModuleConfiguration: KotlinNotebookScriptsModuleConfigurationInfo,
        runtimeLibrary: LibraryEntity
    ) {
        val moduleName = notebookModuleConfiguration.notebookFile.toK2RuntimeModuleName(project)

        val sdkDependency =
            notebookModuleConfiguration.sdkInfo
                ?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

        val source = KotlinNotebookScriptEntitySource(
            notebookModuleConfiguration.notebookFile.toVirtualFileUrl(
                WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
            )
        )

        val dependencies = listOfNotNull(
            LibraryDependency(runtimeLibrary.symbolicId, false, DependencyScope.COMPILE),
            sdkDependency
        )

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