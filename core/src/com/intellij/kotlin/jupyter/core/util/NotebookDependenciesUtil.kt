// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookDependencies
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookPerFileSettingsCache
import com.intellij.kotlin.jupyter.core.settings.findModule
import com.intellij.kotlin.jupyter.core.settings.getSuitableLibraries
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import org.jetbrains.kotlin.idea.util.sourceRoots
import java.nio.file.Path


fun KotlinNotebookDependencies.getSourceRoots(project: Project): Collection<Path> {
    return when (this) {
        is KotlinNotebookDependencies.None -> emptyList()
        is KotlinNotebookDependencies.AllLibraries -> {
            val libraries = getSuitableLibraries(project)
            libraries.flatMap { library ->
                library.rootProvider.getFiles(OrderRootType.SOURCES).map { Path.of(it.path) }
            }
        }
        is KotlinNotebookDependencies.SingleModule -> {
            val tagetModule = findModule(project)
            if (tagetModule == null) return emptyList()
            tagetModule.sourceRoots.map { Path.of(it.path) }
        }
    }
}

fun KotlinNotebookDependencies.arePresent(): Boolean = this != KotlinNotebookDependencies.None

fun BackedNotebookVirtualFile.projectDependencies(project: Project): KotlinNotebookDependencies {
    return KotlinNotebookPerFileSettingsCache.getInstance(project)
        .getSettings(this)
        .notebookDependencies
}
