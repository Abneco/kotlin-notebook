// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.kotlin.jupyter.core.projectModel.createOrUpdateLibraryForNotebookDependencies
import com.intellij.kotlin.jupyter.core.util.getRelativePathFromProjectRoot
import com.intellij.kotlin.jupyter.k2.scriptingSupport.NotebookScriptConfigurationsManager.Companion.NOTEBOOK_MODULE_NAME_PREFIX
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import java.nio.file.Path

internal const val NOTEBOOK_DEPENDENCIES_MODULE_PREFIX = "$KOTLIN_SCRIPTS_MODULE_NAME.Notebook.Dependencies "

fun VirtualFile.toK2RuntimeDependencyLibraryName(project: Project): String {
    val presentableName = getRelativePathFromProjectRoot(project)?.toString() ?: nameWithoutExtension

    return "$NOTEBOOK_DEPENDENCIES_MODULE_PREFIX for ${presentableName} dependencies"
}

fun VirtualFile.toK2RuntimeModuleName(project: Project): String {
    val prefixFromProjectRoot = getRelativePathFromProjectRoot(project)?.parent
    val moduleNamePrefix = if (prefixFromProjectRoot != null) {
        "$NOTEBOOK_MODULE_NAME_PREFIX.$prefixFromProjectRoot"
    } else {
        NOTEBOOK_MODULE_NAME_PREFIX
    }

    val file = Path.of(path).toFile()
    val relativeLocation = file.nameWithoutExtension
    val locationName = relativeLocation.replace(VfsUtilCore.VFS_SEPARATOR_CHAR, ':')

    return "$moduleNamePrefix.$locationName"
}

internal fun VirtualFileUrlManager.getNotebookDependenciesAsLibraryEntity(
    entityStorage: MutableEntityStorage,
    notebookFile: VirtualFile,
    project: Project,
    configuration: ScriptCompilationConfigurationWrapper
): LibraryEntity {
    val url = notebookFile.toVirtualFileUrl(this)
    val notebookEntity = KotlinNotebookScriptEntitySource(url)
    val name = notebookFile.toK2RuntimeDependencyLibraryName(project)
    return entityStorage
        .createOrUpdateLibraryForNotebookDependencies(name, project, notebookEntity, configuration)
}