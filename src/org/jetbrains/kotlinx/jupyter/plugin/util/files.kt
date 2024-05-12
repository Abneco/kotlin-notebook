package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
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

fun Project.isInsideSourceRoot(vFile: VirtualFile): Boolean {
    if (!vFile.isInLocalFileSystem) return false
    val file: File = VfsUtil.virtualToIoFile(vFile)

    val sourceRoots = allSourceRoots()
    return sourceRoots.any { root ->
        file.startsWith(root)
    }
}

fun Path.findNotebookVirtualFileOrNull(): BackedNotebookVirtualFile? {
    val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(this) ?: return null
    return BackedNotebookVirtualFile.find(virtualFile)
}

val VirtualFile.parentsWithSelf: Sequence<VirtualFile> get() = generateSequence(this) { it.parent }

typealias ProjectArtifacts = List<String>

fun VirtualFile.toAbsolutePath(): Path {
    return File(path).absoluteFile.toPath()
}

/**
 * This function extracts a pretty file path as a string relative to the
 * project's root directory with first parent.
 *
 * The first parent is considered, e.g.:
 *    root/abc/a.txt -> "abc/a.txt"
 *    root/a.txt -> "a.txt"
 */
@NlsSafe
fun Path.toRelativePathAsStringFromProjectRoot(project: Project): String {
    val projectPath = project.guessProjectDir()?.runCatching { toNioPath() }?.getOrNull()
    val contentPath = this

    return when {
        contentPath.parent == projectPath -> contentPath.fileName.toString()
        else -> "${contentPath.parent.fileName}/${contentPath.fileName}"
    }
}

fun VirtualFile?.toKotlinNotebookBackedFile(): BackedNotebookVirtualFile? {
    return if (this == null || !isKotlinNotebook) {
        null
    } else {
        toBackedNotebookFile()
    }
}
