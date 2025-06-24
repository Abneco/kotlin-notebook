// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.project.model

import com.intellij.kotlin.jupyter.core.util.getRelativePathFromProjectRoot
import com.intellij.kotlin.jupyter.k2.scriptingSupport.NotebookScriptConfigurationsManager.Companion.NOTEBOOK_MODULE_NAME_PREFIX
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.KOTLIN_SCRIPTS_MODULE_NAME
import java.nio.file.Path


internal const val NOTEBOOK_DEPENDENCIES_MODULE_PREFIX = "$KOTLIN_SCRIPTS_MODULE_NAME.Notebook.Dependencies"

fun VirtualFile.toK2RuntimeDependencyLibraryName(project: Project, typePrefix: String): String {
    val presentableName = getRelativePathFromProjectRoot(project)?.toString() ?: nameWithoutExtension

    return "$NOTEBOOK_DEPENDENCIES_MODULE_PREFIX.$typePrefix for ${presentableName} dependencies"
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
