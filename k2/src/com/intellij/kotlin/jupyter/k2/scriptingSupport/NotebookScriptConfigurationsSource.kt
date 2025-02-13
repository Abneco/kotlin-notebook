// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.projectModel.resolveLibraryDependencies
import com.intellij.kotlin.jupyter.core.util.getRelativePathFromProjectRoot
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
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
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationWithSdk
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationsSource
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
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
    override fun getScriptDefinitionsSource(): ScriptDefinitionsSource? =
        project.scriptDefinitionsSourceOfType<KotlinNotebookScriptDefinitionsSource>()

    override suspend fun updateConfigurations(scripts: Iterable<KotlinNotebookScriptModel>) {
        val sdk = ProjectRootManager.getInstance(project).projectSdk ?: ProjectJdkTable.getInstance().allJdks.firstOrNull()
        if (sdk == null) {
            thisLogger().warn("No JDK SDK is set for the project")
        }

        val configurations = scripts.associate { ktScript ->
            val virtualFile = ktScript.virtualFile
            val configuration = ktScript.refinedConfigurationResult.asSuccess()

            virtualFile to ScriptConfigurationWithSdk(configuration, sdk)
        }

        data.set(configurations)
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


    private fun creteOrUpdateScriptModules(
        project: Project,
        configurationsPerNotebook: Map<VirtualFile, KotlinNotebookScriptsModuleConfigurationInfo>,
        mutableEntityStorage: MutableEntityStorage
    ) {
        val virtualFileManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
        var notebookRuntimeDependencies: LibraryEntity? = null

        for ((notebookFile, moduleConfigurations) in configurationsPerNotebook) {
            notebookRuntimeDependencies = virtualFileManager.getNotebookDependenciesAsLibraryEntity(
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
        const val NOTEBOOK_MODULE_NAME_PREFIX = "$KOTLIN_SCRIPTS_MODULE_NAME.Kotlin Notebooks"
    }
}