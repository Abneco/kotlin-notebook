// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.client.JupyterClient
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterKernelId
import com.intellij.jupyter.core.jupyter.connections.server.JupyterServer
import com.intellij.kotlin.jupyter.core.jupyter.actions.NotebookMode
import com.intellij.kotlin.jupyter.core.jupyter.actions.mode
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.DefaultKotlinKernelConfigFactory
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.ModeAwareKernelRunnableFactory
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.embedded.EmbeddedKernelRunnableFactory
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.extensions.KernelProcessCommandLineCustomizer
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.extensions.KernelVmCommandCustomizer
import com.intellij.kotlin.jupyter.core.jupyter.toolwindow.KotlinNotebookToolWindowManager
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifacts
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifactsDownloader
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookSessionRunMode
import com.intellij.kotlin.jupyter.core.settings.selectedKernelVersionAsString
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlinx.jupyter.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.startup.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.startup.createRandomKernelPorts
import org.jetbrains.kotlinx.jupyter.startup.javaCmdLine
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
class KernelProcessFactory : ModeAwareKernelRunnableFactory(
    KotlinNotebookSessionRunMode.SEPARATE_PROCESS
) {
    @RequiresBackgroundThread
    override fun createKernelRunnableHandler(
        project: Project,
        kernelId: JupyterKernelId,
        notebookPath: Path,
        notebookVirtualFile: BackedNotebookVirtualFile?,
        replCompilerMode: ReplCompilerMode,
    ): SeparateProcessKotlinKernelRunnableHandler {
        val kernelPorts = getKernelPorts()
        val kernelConfig = DefaultKotlinKernelConfigFactory(project, kernelPorts, notebookPath, replCompilerMode).create()

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
                project.selectedKernelVersionAsString
            ).joinToString(classpathSeparator) { it.absolutePath },
            extraJavaArgs,
        )

        val commandLine = GeneralCommandLine(cmdArgs).apply {
            workingDir?.let { withWorkDirectory(it.toFile()) }
            withEnvironment(options.extraEnvironmentVariables)
            KernelProcessCommandLineCustomizer.customize(this)
        }

        return SeparateProcessKotlinKernelRunnableHandler(
            project, kernelId, commandLine, kernelConfig, notebookPath, notebookVirtualFile
        ).apply {
            addKernelListener(object : KotlinKernelProcessListener {
                override fun beforeNotificationStarted(event: KotlinKernelNotificationStartedEvent) {
                    KotlinNotebookToolWindowManager.getInstance(project)
                        .showKotlinNotebookServerManagementToolWindow(
                            KotlinKernelProcessToolWindow(
                                event.source
                            )
                        )
                }
            })
            process.startNotify()
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
        val notebookFile = fileManager.findFileByNioPath(notebookPath) ?: return null
        val mode: NotebookMode = BackedNotebookVirtualFile.takeIfBacked(notebookFile)?.mode ?: return null

        val notebookParentDir = notebookPath.absolute().parent.takeIf { it.exists() }
        return when(mode) {
            NotebookMode.STANDARD -> notebookParentDir
            NotebookMode.LIGHT -> {
                try {
                    project.guessProjectDir()?.toNioPath()
                } catch (_: Exception) {
                    notebookParentDir
                }
            }
        }
    }

    private fun getJavaExecutable(project: Project, options: KotlinNotebookProjectOptionsProvider): String {
        val javaHome: String? = options.jdk.getPath(project) ?: getJavaHomeFromEnvironment()
        if (javaHome == null) return "java"

        val binDir = File(javaHome).absoluteFile.resolve("bin")
        val javaExecutable = sequenceOf("java.exe", "java")
            .map { executableName -> binDir.resolve(executableName) }
            .firstOrNull { executable -> executable.exists() }

        return javaExecutable?.absolutePath ?: "java"
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
