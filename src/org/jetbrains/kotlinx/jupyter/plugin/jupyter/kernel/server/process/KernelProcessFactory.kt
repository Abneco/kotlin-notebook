// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions.NotebookMode
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.actions.mode
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.DefaultKotlinKernelConfigFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KernelRunnableFactory
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded.EmbeddedKernelRunnableFactory
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
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterClient
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterKernelId
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.core.JupyterServer
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Factory for creating Kotlin kernels that will run in their own process. In particular, they
 * will not share the process with neither the [JupyterClient] nor the [JupyterServer].
 *
 * For kernels running in the same process, see [EmbeddedKernelRunnableFactory].
 */
class KernelProcessFactory : KernelRunnableFactory {
    @RequiresBackgroundThread
    override fun createKernelRunnableHandler(
        project: Project,
        kernelId: JupyterKernelId,
        notebookPath: Path,
    ): KotlinKernelProcessHandler? {
        if (project.kotlinNotebookSessionRunMode != KotlinNotebookSessionRunMode.SEPARATE_PROCESS) return null

        val kernelPorts = getKernelPorts()
        val kernelConfig = DefaultKotlinKernelConfigFactory(project, kernelPorts, notebookPath).create()

        val options = KotlinNotebookProjectOptionsProvider.getInstance(project)
        val javaExecutable = getJavaExecutable(project, options)
        val workingDir: Path? = getWorkingDir(project, notebookPath)

        val extraJavaArgs = buildList {
            workingDir?.let { add("-Duser.dir=${workingDir.invariantSeparatorsPathString}/") }
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

    /**
     * Notebooks that are [NotebookMode.LIGHT] are stored outside the project directory,
     * so for them, we change the working directory to be the project root dir. This is
     * so relative paths are resolved with respect to the project. Otherwise, we just use the
     * directory the notebook is in.
     *
     * Note, there could be no physical working directory if the kernel is started from test
     * and the notebook file is in i.e. [com.intellij.openapi.vfs.ex.temp.TempFileSystem]
     */
    private fun getWorkingDir(project: Project, notebookPath: Path): Path? {
        val fileManager = VirtualFileManager.getInstance()
        val mode: NotebookMode = fileManager.findFileByNioPath(notebookPath)?.let { notebookFile ->
            BackedNotebookVirtualFile.find(notebookFile)?.mode
        } ?: return null

        val notebookParentDir = notebookPath.absolute().parent.takeIf { it.exists() }
        return when(mode) {
            NotebookMode.STANDARD -> notebookParentDir
            NotebookMode.LIGHT -> {
                try {
                    project.guessProjectDir()?.toNioPath()
                } catch (ex: Exception) {
                    notebookParentDir
                }
            }
        }
    }

    private fun getJavaExecutable(project: Project, options: KotlinNotebookProjectOptionsProvider): String {
        val javaHome: String? = options.jdk.getPath(project) ?: getJavaHomeFromEnvironment()
        if (javaHome == null) return "java"

        val binDir = File(javaHome).absoluteFile.resolve("bin")
        val javaExec =  binDir.resolve(if (SystemInfo.isWindows) "java.exe" else "java")
        return javaExec.absolutePath
    }

    private val javaHomeEnvironmentVariablesToTry = listOf(
        "KOTLIN_JUPYTER_JAVA_HOME",
        "JRE_HOME",
        "JDK_HOME",
        "JDK_11",
        "JAVA_HOME",
    )

    private fun getJavaHomeFromEnvironment(): String? {
        return javaHomeEnvironmentVariablesToTry.firstNotNullOfOrNull { variableName ->
            System.getenv(variableName)?.takeIf { it.isNotBlank() }
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
