// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.DefaultKotlinKernelConfigFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KernelRunnableFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.extensions.KernelProcessCommandLineCustomizer
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.extensions.KernelVmCommandCustomizer
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.kotlinNotebookSessionRunMode
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifactsDownloader
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookSessionRunMode
import org.jetbrains.kotlinx.jupyter.plugin.settings.getSelectedKernelVersion
import org.jetbrains.kotlinx.jupyter.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.startup.createRandomKernelPorts
import org.jetbrains.kotlinx.jupyter.startup.javaCmdLine
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterKernelId
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

class KernelProcessFactory : KernelRunnableFactory {
    @RequiresBackgroundThread
    override fun createKernelRunnableHandler(
        project: Project,
        kernelId: JupyterKernelId,
        notebookPath: Path,
    ): KotlinKernelProcessHandler? {
        if (project.kotlinNotebookSessionRunMode != KotlinNotebookSessionRunMode.SEPARATE_PROCESS) return null

        val kernelPorts = getKernelPorts()
        val kernelConfig = DefaultKotlinKernelConfigFactory(project, kernelPorts).create()

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

        val classpathSeparator = File.pathSeparator
        val cmdArgs = kernelConfig.javaCmdLine(
            javaExecutable,
            "kernelProcessConnection",
            KotlinNotebookMavenArtifactsDownloader.getInstance(project).downloadArtifactBlocking(
                KotlinNotebookMavenArtifacts.KERNEL_SHADOWED,
                getSelectedKernelVersion(project)
            ).joinToString(classpathSeparator) { it.absolutePath },
            extraJavaArgs
        )

        val commandLine = GeneralCommandLine(cmdArgs).apply {
            workingDir?.let { withWorkDirectory(it.toFile()) }
            withEnvironment(options.extraEnvironmentVariables)
            KernelProcessCommandLineCustomizer.customize(this)
        }

        return KotlinKernelProcessHandler(
            project, kernelId, commandLine, kernelConfig, notebookPath
        ).apply {
            addKernelProcessListener(object : KotlinKernelProcessListener {
                override fun beforeNotificationStarted(event: KotlinKernelNotificationStartedEvent) {
                    val application = ApplicationManager.getApplication()
                    if (!application.isUnitTestMode) {
                        application.invokeLater {
                            showKotlinNotebookServerManagementToolWindow(event.source)
                        }
                    }
                }
            })
            startNotify()
        }
    }

    private var _kernelPortsProvider: KernelPortsProvider = KernelPortsProvider {
        createRandomKernelPorts()
    }

    val kernelPortsProvider: KernelPortsProvider get() = _kernelPortsProvider

    @TestOnly
    fun setKernelPortsProvider(kernelPortsProvider: KernelPortsProvider) {
        _kernelPortsProvider = kernelPortsProvider
    }

    private fun getKernelPorts(): KernelPorts {
        return _kernelPortsProvider.getKernelPorts()
    }
}
