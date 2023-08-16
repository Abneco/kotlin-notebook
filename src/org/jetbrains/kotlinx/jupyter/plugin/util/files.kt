package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.util.sourceRoots
import java.io.File

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

val VirtualFile.parentsWithSelf: Sequence<VirtualFile> get() = generateSequence(this) { it.parent }

typealias ProjectArtifacts = List<String>
