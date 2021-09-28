package org.jetbrains.kotlinx.jupyter.plugin.util

import com.intellij.util.io.isFile
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
