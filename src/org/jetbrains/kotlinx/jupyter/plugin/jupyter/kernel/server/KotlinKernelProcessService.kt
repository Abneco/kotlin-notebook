// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.ZipUtil
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.extensions.KernelVmCommandCustomizer
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.util.KotlinJupyterResourcesUtil
import org.jetbrains.kotlinx.jupyter.startup.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolute
import kotlin.io.path.exists

@Service(Service.Level.APP)
class KotlinKernelProcessService {
    companion object {
        @JvmStatic
        fun getInstance(): KotlinKernelProcessService = service()

        @JvmStatic
        private val LOG = logger<KotlinKernelProcessService>()
    }

    private val homeDirectory by lazy {
        PathManager.getSystemDir().resolve("kotlin-jupyter").resolve("kernelProcess").toFile()
    }

    private val kernelJars by lazy {
        val kernelJarsDir = homeDirectory.resolve("kernel")
        unzipResourceSafe("kernel.zip", kernelJarsDir)
    }
    private val scriptJars by lazy {
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

    private fun unzipResource(resourceZipPath: String, dir: File): List<File> {
        // There are two places where ZIP with JARs may be:
        // 1. On classpath / in JAR resources. In this case we extract it as a resource
        // 2. In plugin resources. In this case the extraction is a bit different
        dir.mkdirs()
        val zipPath = dir.resolve(resourceZipPath).toPath()

        val resourceStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourceZipPath)
        if (resourceStream != null) {
            Files.copy(resourceStream, zipPath, StandardCopyOption.REPLACE_EXISTING)
        } else {
            val pluginResourcePath = KotlinJupyterResourcesUtil.getPluginResource(resourceZipPath)?.toPath()
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

    fun create(
        project: Project,
        notebookPath: Path,
        onBeforeStartNotify: (KotlinKernelProcessHandler) -> Unit,
        onKernelTerminated: (ProcessEvent, KotlinKernelProcessHandler) -> Unit
    ): KotlinKernelProcessHandler {
        val kernelConfig = KernelConfig(
            createRandomKernelPorts(),
            "tcp",
            "HmacSHA256",
            "x-x-x",
            scriptJars,
            null,
            null,
            "kotlin_notebook",
            // TODO: make it an option
            jvmTargetForSnippets = null,
        )

        val classpathSeparator = System.getProperty("path.separator")

        val options = KotlinNotebookProjectOptionsProvider.getInstance(project).state
        val javaExecutable = options.jdk.getPath(project)?.let { javaHome ->
            val binDir = File(javaHome).absoluteFile.resolve("bin")
            val javaExec = if (SystemInfo.isWindows) binDir.resolve("java.exe")
            else binDir.resolve("java")
            javaExec.absolutePath
        } ?: "java"

        /** There could be no physical working directory if the kernel is started from test
         and the notebook file is in i.e. [com.intellij.openapi.vfs.ex.temp.TempFileSystem] */
        val workingDir = notebookPath.absolute().parent.takeIf { it.exists() }

        val extraJavaArgs = buildList {
            workingDir?.let { add("-Duser.dir=${workingDir.systemIndependentPath}/") }
            add("-Xmx${options.heapMaxLimitInMib}M")
            for (extraArg in options.extraJvmArguments) {
                add(extraArg)
            }
            KernelVmCommandCustomizer.addVmArguments(this)
        }

        val cmdArgs = kernelConfig.javaCmdLine(
            javaExecutable,
            "kernelProcessConnection",
            kernelJars.joinToString(classpathSeparator) { it.absolutePath },
            extraJavaArgs
        )

        val commandLine = GeneralCommandLine(cmdArgs).apply {
            workingDir?.let { withWorkDirectory(it.toFile()) }
        }

        return KotlinKernelProcessHandler(
            commandLine, kernelConfig, notebookPath,
            onKernelTerminated
        ).also { processHandler ->
            val application = ApplicationManager.getApplication()
            if (application.isUnitTestMode) {
                processHandler.startNotify()
            } else {
                ApplicationManager.getApplication().invokeLater {
                    onBeforeStartNotify(processHandler)

                    processHandler.startNotify()
                }
            }
        }
    }
}