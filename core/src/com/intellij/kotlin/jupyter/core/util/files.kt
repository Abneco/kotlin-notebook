// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.util

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.ui.content.ContentManager
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.idea.util.sourceRoots
import java.io.File
import java.nio.file.Path

val File.isNotEmptyDirectory: Boolean
    get() = exists() && isDirectory && (list()?.isNotEmpty() ?: false)

fun Project.allSourceRoots(): List<File> {
    val moduleManager = ModuleManager.getInstance(this)
    val allModules = moduleManager.modules
    return allModules.flatMap { module ->
        module.sourceRoots.map { File(it.path) }
    }
}

fun Path.findNotebookVirtualFileOrNull(): BackedNotebookVirtualFile? {
    val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(this) ?: return null
    return BackedNotebookVirtualFile.Companion.takeBackend(virtualFile)
}

fun Project.findEditors(virtualFile: VirtualFile): List<Editor> {
    val fileEditorManager = FileEditorManager.getInstance(this)
    return fileEditorManager.getAllEditors(virtualFile)
        .filterIsInstance<TextEditor>()
        .map { it.editor }
}

val VirtualFile.parentsWithSelf: Sequence<VirtualFile> get() = generateSequence(this) { it.parent }

typealias ProjectArtifacts = List<String>

fun VirtualFile.toAbsolutePath(): Path {
    return File(path).absoluteFile.toPath()
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

/**
 * Returns relative [Path] from [Project] root, or plain path otherwise
 */
fun VirtualFile.getRelativePathFromProjectRoot(project: Project): Path? {
    val projectRoot = project.guessProjectDir()
    if (projectRoot == null) {
        return toNioPathOrNull()
    }

    return VfsUtilCore.getRelativePath(this, projectRoot)?.toNioPathOrNull()
}
