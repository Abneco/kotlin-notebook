// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.projectModel.resolveLibraryDependencies
import com.intellij.kotlin.jupyter.core.settings.ProjectJdkOption
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.kotlin.jupyter.core.util.getRelativePathFromProjectRoot
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.blockingContextScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.vfs.VfsUtilCore
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
import kotlinx.coroutines.async
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.analysisContextModule
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationsSource
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import java.nio.file.Path
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
 *  Note that now for each script a separate module is created, and for each module there are its own dependencies.
 *  This is about to change.
 */
class NotebookScriptConfigurationsSource(override val project: Project) : ScriptConfigurationsSource<KotlinNotebookScriptModel>(project) {
    override fun getDefinitions(): Sequence<ScriptDefinition>? =
        project.scriptDefinitionsSourceOfType<KotlinNotebookScriptDefinitionsSource>()?.definitions

    /**
     * Unfortunately, we live in the injection world.
     * There might be a situation where [VirtualFileWindow] gets invalided soon after it was processed.
     * In this case, we should try to fall back and pick any other configuration from the Notebook.
     */
    override fun getConfigurationWithSdk(virtualFile: VirtualFile): ScriptConfigurationWithSdk? {
        val stored = super.getConfigurationWithSdk(virtualFile)
        if (stored != null) return stored

        val cache = data.get()
        if (cache.isEmpty()) return null
        val notebooksCache = cache.toConfigurationInfoPerNotebook()

        val topLevelFile = (virtualFile as? VirtualFileWindow)?.delegate ?: return null
        val notebookScriptsCache = notebooksCache[topLevelFile]?.scripts ?: return null
        if (notebookScriptsCache.isEmpty()) return null

        val recordWithValidWindow = notebookScriptsCache.firstOrNull {
            it.first.isValid
        }
        if (recordWithValidWindow == null) return null

        val (virtualFileWindow, otherConfigurationFromNotebook) = recordWithValidWindow

        // try to add data to the cache
        KotlinNotebookPluginScope.getForProject(project).async {
            val scriptWithSdk = ScriptConfigurationWithSdk(
                otherConfigurationFromNotebook.asSuccess(),
                notebooksCache[topLevelFile]?.sdkInfo
            )

            data.accumulateAndGet(
                mapOf(virtualFile to scriptWithSdk)
            ) { old, new -> old + new }

            // add another module as a context dependency until the update is performed
            readAction {
                virtualFile.setUpTemporaryModuleForAnalysis(virtualFileWindow)
            }
        }

        return cache[virtualFileWindow]
    }

    @OptIn(KaImplementationDetail::class)
    override suspend fun updateConfigurations(scripts: Iterable<KotlinNotebookScriptModel>) {
        val sdk = ProjectJdkOption.getSdk(project) ?: ProjectJdkTable.getInstance().allJdks.firstOrNull()
        if (sdk == null) {
            notebookLogger().warn("No JDK SDK is set for the project")
        }

        val configurations = scripts.associate { ktScript ->
            val virtualFile = ktScript.virtualFile
            virtualFile.analysisContextModule = null
            val configuration = ktScript.refinedConfigurationResult.asSuccess()

            virtualFile to ScriptConfigurationWithSdk(configuration, sdk)
        }

        // incremental updates are supported
        val trimmedCache = data.get().toMutableMap()
            .removeOverlappingRecords(configurations)

        data.set(trimmedCache + configurations)
    }

    /**
     * Removes all records related to notebook files before putting new ones from [configurationsUpdate]
     */
    private fun MutableMap<VirtualFile, ScriptConfigurationWithSdk>.removeOverlappingRecords(
        configurationsUpdate: Map<VirtualFile, ScriptConfigurationWithSdk>
    ): MutableMap<VirtualFile, ScriptConfigurationWithSdk> {
        val updatesPerNotebookFile = configurationsUpdate.toConfigurationInfoPerNotebook()
        val keysToRemove = keys.filter { (it as VirtualFileWindow).delegate in updatesPerNotebookFile }
        keys.removeAll(keysToRemove)
        return this
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

    override suspend fun updateModules(storage: MutableEntityStorage?) {
        val workspaceModel = project.workspaceModel
        val workspaceSnapshot = storage?.toSnapshot() ?: workspaceModel.currentSnapshot
        val tmp = MutableEntityStorage.from(workspaceSnapshot)

        val configurationsByNotebook = data.get().toConfigurationInfoPerNotebook()
        creteOrUpdateScriptModules(project, configurationsByNotebook, tmp)

        workspaceModel.update("Updating Kotlin Notebook scripting modules") { model ->
            // add new data, target only the base K2 script source
            model.replaceBySource({ it is KotlinNotebookScriptEntitySource }, tmp)
        }
    }


    private suspend fun creteOrUpdateScriptModules(
        project: Project,
        configurationsPerNotebook: Map<VirtualFile, KotlinNotebookScriptsModuleConfigurationInfo>,
        mutableEntityStorage: MutableEntityStorage
    ) {
        val virtualFileManager = blockingContextScope {
            WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
        }

        for ((notebookFile, moduleConfigurations) in configurationsPerNotebook) {
            val notebookRuntimeDependencies = virtualFileManager.getNotebookDependenciesAsLibraryEntity(
                mutableEntityStorage,
                notebookFile,
                project,
                moduleConfigurations.scripts.first().second
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
        val prefixFromProjectRoot = notebookModuleConfiguration.notebookFile.getRelativePathFromProjectRoot(project)?.parent
        val moduleNamePrefix = if (prefixFromProjectRoot != null) {
            "$NOTEBOOK_MODULE_NAME_PREFIX.$prefixFromProjectRoot"
        } else {
            NOTEBOOK_MODULE_NAME_PREFIX
        }

        for ((scriptFile, _) in notebookModuleConfiguration.scripts) {
            val file = Path.of(scriptFile.path).toFile()
            val relativeLocation = file.nameWithoutExtension

            val locationName = relativeLocation.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')
            val moduleName = "$moduleNamePrefix.$locationName"

            val sdkDependency =
                notebookModuleConfiguration.sdkInfo
                    ?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

            val source = KotlinNotebookScriptEntitySource(scriptFile.toVirtualFileUrl(WorkspaceModel.getInstance(project).getVirtualFileUrlManager()))

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
                continue
            }

            // seen firstly
            mutableEntityStorage.addEntity(newEntry)
        }
    }

    companion object {
        const val NOTEBOOK_MODULE_NAME_PREFIX: String = "$KOTLIN_SCRIPTS_MODULE_NAME.Kotlin Notebooks"
    }
}