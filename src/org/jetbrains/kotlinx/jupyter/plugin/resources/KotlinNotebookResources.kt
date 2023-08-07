// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.resources

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.ZipUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Service(Service.Level.APP)
class KotlinNotebookResources {
    companion object {
        @JvmStatic
        fun getInstance(): KotlinNotebookResources = service()

        @JvmStatic
        private val LOG = logger<KotlinNotebookResources>()
    }

    private val homeDirectory by lazy {
        PathManager.getSystemDir().resolve("kotlin-jupyter").resolve("kernelProcess").toFile()
    }

    val kernelJars by lazy {
        val kernelJarsDir = homeDirectory.resolve("kernel")
        unzipResourceSafe("kernel.zip", kernelJarsDir)
    }
    val scriptJars by lazy {
        val scriptJarsDir = homeDirectory.resolve("lib")
        unzipResourceSafe("lib.zip", scriptJarsDir)
    }
    val ideJars by lazy {
        val ideScriptJarsDir = homeDirectory.resolve("ideLib")
        unzipResourceSafe("ideLib.zip", ideScriptJarsDir)
    }
    val libSourcesJars by lazy {
        val libSourcesJarsDir = homeDirectory.resolve("libSources")
        unzipResourceSafe("libSources.zip", libSourcesJarsDir)
    }

    private fun unzipResourceSafe(resourceZipPath: String, dir: File): List<File> {
        fun handle(e: Exception): List<File> {
            LOG.warn("Unable to load resource $resourceZipPath", e)
            return emptyList()
        }

        return try {
            unzipResource(resourceZipPath, dir)
        } catch (e: RuntimeException) {
            handle(e)
        } catch (e: IOException) {
            handle(e)
        }
    }

    private fun unzipResource(resourceZipPath: String, dir: File): List<File> { // There are two places where ZIP with JARs may be:
        // 1. On classpath / in JAR resources. In this case we extract it as a resource
        // 2. In plugin resources. In this case the extraction is a bit different
        dir.mkdirs()
        val zipPath = dir.resolve(resourceZipPath).toPath()

        val resourceStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourceZipPath)
        if (resourceStream != null) {
          Files.copy(resourceStream, zipPath, StandardCopyOption.REPLACE_EXISTING)
        } else {
            val pluginResourcePath = KotlinNotebookResourcesUtil.getPluginResource(resourceZipPath)?.toPath()
            if (pluginResourcePath != null) {
              Files.copy(pluginResourcePath, zipPath, StandardCopyOption.REPLACE_EXISTING)
            } else {
                val errorMessage = buildString {
                    append("There is no resource $resourceZipPath neither in JAR resources nor in plugin resources.")
                    if (ApplicationManager.getApplication().isUnitTestMode) {
                        append(" Execute `Prepare Kotlin Notebook resources` run configuration to download required files.")
                    }
                }
                throw RuntimeException(errorMessage)
            }
        }

        val files = mutableListOf<File>()
      ZipUtil.extract(zipPath, dir.toPath()) { fileDir, fileName ->
        val file = fileDir.resolve(fileName)
        files.add(file)
        true
      }
      Files.delete(zipPath)

        return files
    }
}