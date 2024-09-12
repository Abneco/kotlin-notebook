// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.k2

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEPENDENCIES_SOURCES
import org.jetbrains.kotlin.idea.core.script.createLibraryDependency
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesData
import org.jetbrains.kotlin.idea.core.script.k2.ScriptDependenciesSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.nio.file.Path
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.valueOrNull


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

        creteOrUpdateScriptModules(project, dependencies, tmp)

        workspaceModel.update("Updating Kotlin Notebook scripting modules") { model ->
            // add new data
            model.applyChangesFrom(tmp)
        }
    }


    private fun creteOrUpdateScriptModules(
        project: Project,
        dependenciesData: ScriptDependenciesData,
        mutableEntityStorage: MutableEntityStorage
    ) {
        val sourcesToUpdate: MutableSet<KotlinScriptEntitySource> = mutableSetOf()

        for ((scriptFile, configurationWrapper) in dependenciesData.configurations) {
            if (ScratchUtil.isScratch(scriptFile)) {
                continue
            }

            val configuration = configurationWrapper.valueOrNull() ?: continue

            val file = Path.of(scriptFile.path).toFile()
            //val relativeLocation = FileUtil.getRelativePath(projectPath.toFile(), file) ?: continue
            val relativeLocation = file.nameWithoutExtension

            val definitionName = findScriptDefinition(project, VirtualFileScriptSource(scriptFile)).name

            val definitionScriptModuleName = "$KOTLIN_SCRIPTS_MODULE_NAME.$definitionName"
            val locationName = relativeLocation.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')
            val moduleName = "$definitionScriptModuleName.$locationName"

            val sdkDependency =
                configuration.javaHome?.toPath()
                    ?.let { dependenciesData.sdks[it] }
                    ?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

            val source = KotlinScriptEntitySource(scriptFile.toVirtualFileUrl(WorkspaceModel.getInstance(project).getVirtualFileUrlManager()))
            sourcesToUpdate += source

            val dependencies = listOfNotNull(
                mutableEntityStorage.createLibraryDependency(moduleName, project, source, configuration),
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
        fun getInstance(project: Project): NotebookScriptDependenciesSource? =
            SCRIPT_DEPENDENCIES_SOURCES.getExtensions(project)
                .filterIsInstance<NotebookScriptDependenciesSource>().firstOrNull()
                .safeAs<NotebookScriptDependenciesSource>()
    }
}