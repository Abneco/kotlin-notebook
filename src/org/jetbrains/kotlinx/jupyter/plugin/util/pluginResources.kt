// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.util

import org.jetbrains.kotlinx.jupyter.plugin.JupyterCompilerService
import java.io.File
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.Files

fun ClassLoader.unpackResourcesDirectory(path: String): File? {
    val url: URL? = getResource(path)
    if (url == null) return null
    val file = try {
        File(url.toURI())
    } catch (e: URISyntaxException) {
        File(url.path)
    }

    val kernelJarsDir = Files.createTempDirectory("kotlin-kernel-jars").toFile()
    val isSuccess = file.copyRecursively(kernelJarsDir, overwrite = true)
    if (!isSuccess) return null

    return kernelJarsDir
}

fun unpackKernelJars(): File? {
    return JupyterCompilerService::class.java.classLoader.unpackResourcesDirectory("kernelJars")
}
