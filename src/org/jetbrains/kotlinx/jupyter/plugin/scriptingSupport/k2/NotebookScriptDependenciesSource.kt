// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.k2

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.sourceRoots
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEPENDENCIES_SOURCES
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesData
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesSource
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlinx.jupyter.plugin.projectModel.getNotebookDependenciesAsLibraryEntity
import java.nio.file.Path
import kotlin.script.experimental.api.asSuccess


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
class NotebookScriptDependenciesSource(override val project: Project) : ScriptDependenciesSource<KotlinNotebookScriptModel>(project) {
    override fun resolveDependencies(scripts: Iterable<KotlinNotebookScriptModel>): ScriptDependenciesData {
        val sdk = ProjectRootManager.getInstance(project).projectSdk

        val configurations = scripts.associate { ktScript ->
            val virtualFile = ktScript.virtualFile
            val configuration = ktScript.refinedConfigurationResult.asSuccess()

            virtualFile to configuration
        }

        return ScriptDependenciesData(
            configurations,
            sdks = sdk?.homePath?.let<@NonNls String, Map<Path, Sdk>> { mapOf(Path.of(it) to sdk) } ?: emptyMap()
        )
    }

    override suspend fun updateModules(dependencies: ScriptDependenciesData, storage: MutableEntityStorage?) {
        val workspaceModel = project.workspaceModel
        val workspaceSnapshot = storage?.toSnapshot() ?: workspaceModel.currentSnapshot
        val tmp = MutableEntityStorage.from(workspaceSnapshot)

        val configurationsByNotebook = dependencies.toConfigurationInfoPerNotebook()
        creteOrUpdateScriptModules(project, configurationsByNotebook, tmp)

        workspaceModel.update("Updating Kotlin Notebook scripting modules") { model ->
            // add new data
            model.applyChangesFrom(tmp)
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

    private fun updateNotebookConfiguration(
        project: Project,
        mutableEntityStorage: MutableEntityStorage,
        notebookModuleConfiguration: KotlinNotebookScriptsModuleConfigurationInfo,
        runtimeLibrary: LibraryEntity
    ) {
        for ((scriptFile, configuration) in notebookModuleConfiguration.scripts) {
            val file = Path.of(scriptFile.path).toFile()
            val relativeLocation = file.nameWithoutExtension

            val locationName = relativeLocation.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')
            val moduleName = "$NOTEBOOK_MODULE_NAME_PREFIX.$locationName"

            val sdkDependency =
                configuration.javaHome?.toPath()
                    ?.let { notebookModuleConfiguration.sdkInfo }
                    ?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

            val source = KotlinScriptEntitySource(scriptFile.toVirtualFileUrl(WorkspaceModel.getInstance(project).getVirtualFileUrlManager()))

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

        fun getInstance(project: Project): NotebookScriptDependenciesSource? =
            SCRIPT_DEPENDENCIES_SOURCES.getExtensions(project)
                .filterIsInstance<NotebookScriptDependenciesSource>().firstOrNull()
                .safeAs<NotebookScriptDependenciesSource>()
    }
}