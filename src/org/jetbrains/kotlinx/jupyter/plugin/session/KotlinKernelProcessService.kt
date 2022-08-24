// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.session

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.io.ZipUtil
import org.jetbrains.kotlinx.jupyter.plugin.util.KotlinJupyterResourcesUtil
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.createKernelPorts
import org.jetbrains.kotlinx.jupyter.startup.javaCmdLine
import java.io.File
import java.nio.file.Files

@Service(Service.Level.APP)
class KotlinKernelProcessService {
    companion object {
        @JvmStatic
        fun getInstance(): KotlinKernelProcessService = service()
    }

    private val portsGenerator = KernelPortsGenerator(32768, 65536)
    private val homeDirectory by lazy {
        Files.createTempDirectory("kernelProcess").toFile()
    }

    private val ideScriptJarsDir by lazy {
        homeDirectory.resolve("ideLib")
    }

    private val kernelJars by lazy {
        val kernelJarsDir = homeDirectory.resolve("kernel")
        unzipResource("kernel.zip", kernelJarsDir)
    }
    private val scriptJars by lazy {
        val scriptJarsDir = homeDirectory.resolve("lib")
        unzipResource("lib.zip", scriptJarsDir)
    }
    val ideJars by lazy {
        unzipResource("ideLib.zip", ideScriptJarsDir)
    }

    private fun unzipResource(resourceZipPath: String, dir: File): List<File> {
        // There are two places where ZIP with JARs may be:
        // 1. On classpath / in JAR resources. In this case we extract it as a resource
        // 2. In plugin resources. In this case the extraction is a bit different
        dir.mkdirs()
        val zipPath = dir.resolve(resourceZipPath).toPath()

        val resourceStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourceZipPath)
        if (resourceStream != null) {
            Files.copy(resourceStream, zipPath)
        } else {
            val pluginResourcePath = KotlinJupyterResourcesUtil.getPluginResource(resourceZipPath)?.toPath()
            if (pluginResourcePath != null) {
                Files.copy(pluginResourcePath, zipPath)
            } else {
                throw RuntimeException("There is no resource $resourceZipPath neither in JAR resources nor in plugin resources")
            }
        }

        ZipUtil.extract(zipPath, dir.toPath(), null)
        Files.delete(zipPath)

        return dir.walkTopDown().filter { it.isFile }.toList()
    }

    val scriptClassPathDir: File get() = ideScriptJarsDir

    fun create(): KotlinKernelProcessHandler {
        val kernelConfig = KernelConfig(
            createKernelPorts { portsGenerator.randomPort() },
            "tcp",
            "HmacSHA256",
            "x-x-x",
            scriptJars,
            homeDirectory,
            null
        )

        val classpathSeparator = System.getProperty("path.separator")

        val cmdArgs = kernelConfig.javaCmdLine(
            "java",
            "kernelProcessConnection",
            kernelJars.joinToString(classpathSeparator) { it.absolutePath }
        )

        val cmd = GeneralCommandLine(cmdArgs)

        return KotlinKernelProcessHandler(cmd, kernelConfig).apply {
            startNotify()
        }
    }
}