package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.isFile
import org.jetbrains.kotlin.idea.util.sourceRoots
import java.io.File
import java.nio.file.Files
import kotlin.streams.toList

val File.isNotEmptyDirectory: Boolean
    get() = exists() && isDirectory && (list()?.isNotEmpty() ?: false)

fun File.allJarsFromDir(): List<File> {
    return Files.walk(this.toPath()).filter { path ->
        path.isFile() && !path.fileName.toString().contains("kotlin-jupyter-kernel")
    }.map {
        it.toFile()
    }.toList()
}

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

typealias ProjectArtifacts = List<String>
