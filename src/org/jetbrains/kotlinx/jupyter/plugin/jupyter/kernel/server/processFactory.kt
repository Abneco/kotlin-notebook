// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.extensions.KernelVmCommandCustomizer
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifactsDownloader
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.startup.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

@RequiresBackgroundThread
fun createKernelProcess(
    project: Project,
    notebookPath: Path,
    onBeforeStartNotify: (KotlinKernelProcessHandler) -> Unit,
    onKernelTerminated: (ProcessEvent, KotlinKernelProcessHandler) -> Unit
): KotlinKernelProcessHandler {
    val mavenArtifactsDownloader = KotlinNotebookMavenArtifactsDownloader.getInstance(project)
    val kernelConfig = KernelConfig(
        createRandomKernelPorts(),
        "tcp",
        "HmacSHA256",
        "x-x-x",
        mavenArtifactsDownloader.downloadArtifactBlocking(KotlinNotebookMavenArtifacts.SCRIPT_CLASSPATH_SHADOWED),
        null,
        null,
        "kotlin_notebook", // TODO: make it an option
        jvmTargetForSnippets = null,
    )

    val classpathSeparator = System.getProperty("path.separator")

    val options = KotlinNotebookProjectOptionsProvider.getInstance(project)
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
        mavenArtifactsDownloader.downloadArtifactBlocking(KotlinNotebookMavenArtifacts.KERNEL_SHADOWED).joinToString(classpathSeparator) { it.absolutePath },
        extraJavaArgs
    )

    val commandLine = GeneralCommandLine(cmdArgs).apply {
        workingDir?.let { withWorkDirectory(it.toFile()) }
    }

    return KotlinKernelProcessHandler(
        commandLine, kernelConfig, notebookPath, onKernelTerminated
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
