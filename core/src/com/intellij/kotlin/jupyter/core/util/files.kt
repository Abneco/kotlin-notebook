// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookDependencies
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookPerFileSettingsCache
import com.intellij.kotlin.jupyter.core.settings.findModule
import com.intellij.kotlin.jupyter.core.settings.getSuitableLibraries
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.idea.util.sourceRoots
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute

val Path.isNotEmptyDirectory: Boolean
    get() = Files.isDirectory(this) &&
            Files.exists(this) &&
            Files.newDirectoryStream(this).use { it.iterator().hasNext() }

fun Project.sourceRootsForDependencies(notebookFile: BackedNotebookVirtualFile): List<Path> {
    val optionsProvider = KotlinNotebookPerFileSettingsCache.getInstance(this).getSettings(notebookFile)
    val dependencies = optionsProvider.notebookDependencies

    return when (dependencies) {
        is KotlinNotebookDependencies.None -> emptyList()
        is KotlinNotebookDependencies.AllLibraries -> {
            val libraries = getSuitableLibraries(this)
            libraries.flatMap { library ->
                library.rootProvider.getFiles(OrderRootType.SOURCES).map { Path.of(it.path) }
            }
        }
        is KotlinNotebookDependencies.SingleModule -> {
            val tagetModule = optionsProvider.notebookDependencies.findModule(this)
            if (tagetModule == null) return emptyList()

            tagetModule.sourceRoots.map { Path.of(it.path) }
        }
    }
}

val VirtualFile.parentsWithSelf: Sequence<VirtualFile> get() = generateSequence(this) { it.parent }

typealias ProjectArtifacts = List<String>

fun VirtualFile.toAbsolutePath(): Path {
    return Path.of(path).absolute()
}

@NlsSafe
@RequiresBackgroundThread
internal fun BackedNotebookVirtualFile.toPresentablePathAsTabTitle(
    project: Project,
    contentManager: ContentManager
): String {
    ThreadingAssertions.assertBackgroundThread()
    val originFile = originFile
    val simpleName = EditorTabPresentationUtil.getEditorTabTitle(project, originFile)
    return if (contentManager.findContent(simpleName) != null) {
        EditorTabPresentationUtil.getUniqueEditorTabTitle(project, originFile)
    } else {
        simpleName
    }
}

fun VirtualFile?.toKotlinNotebookBackedFile(): BackedNotebookVirtualFile? {
    return if (this == null || !isKotlinNotebook) {
        null
    } else {
        toBackedNotebookFile()
    }
}

@Suppress("IO_FILE_USAGE") // There is no alternative in java.nio
val pathSeparator: String get() = java.io.File.pathSeparator
