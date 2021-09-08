package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.isFile
import org.jetbrains.kotlin.idea.util.sourceRoots
import java.io.File
import java.nio.file.Files
import kotlin.streams.toList

val File.isNotEmptyDirectory: Boolean
  get() = exists() && isDirectory && list()?.isEmpty()?.not() ?: false

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

typealias ProjectArtifacts = List<String>
