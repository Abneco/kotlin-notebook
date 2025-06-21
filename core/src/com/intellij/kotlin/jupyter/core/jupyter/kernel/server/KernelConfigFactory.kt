// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.kotlin.jupyter.core.debug.session.KotlinNotebookDebugSessionManager
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifacts
import com.intellij.kotlin.jupyter.core.resources.KotlinNotebookMavenArtifactsDownloader
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.selectedKernelVersionAsString
import com.intellij.kotlin.jupyter.core.settings.ui.maxBytecodeVersion
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.lang.JavaVersion
import org.jetbrains.kotlinx.jupyter.api.JupyterClientType
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.startup.ANY_HOST_NAME
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.startup.parameters.KernelOwnParams
import java.io.File
import java.nio.file.Path

interface KernelConfigFactory {
    fun create(): KernelConfig
}

abstract class AbstractKotlinKernelConfigFactory(
    protected val project: Project,
    protected val notebookPath: Path,
    protected val replCompilerMode: ReplCompilerMode,
) : KernelConfigFactory {
    final override fun create(): KernelConfig = KernelConfig(
        jupyterParams = KernelJupyterParams(
            host = ANY_HOST_NAME,
            ports = getKernelPorts(),
            transport = "tcp",
            signatureScheme = "HmacSHA256",
            // Key doesn't matter as long as it's a local kernel
            signatureKey = "x-x-x",
        ),
        ownParams = KernelOwnParams(
            scriptClasspath = getClasspath(),
            // Don't try to resolve libraries against some local directory,
            // use only embedded or remote JSON library files
            homeDir = null,
            debugPort = getDebugPortOrNull(notebookPath),
            // Kernel provides API for a user to learn in what environment the session is run
            // In particular, a client type is available via `notebook.jupyterClientType`
            // in both separate and embedded modes
            clientType = JupyterClientType.KOTLIN_NOTEBOOK.name,
            jvmTargetForSnippets = getJvmTargetForSnippets(project)?.toFeatureString(),
            replCompilerMode = replCompilerMode
        ),


    )

    protected abstract fun getKernelPorts(): KernelPorts

    protected abstract fun getDebugPortOrNull(notebookPath: Path): Int?

    protected open fun getClasspath(): List<File> {
        return KotlinNotebookMavenArtifactsDownloader.getInstance(project).getClasspathArtifacts(project)
    }

    protected open fun getJvmTargetForSnippets(project: Project): JavaVersion? {
        return chooseJvmTargetForSnippets(project)?.toJavaVersion()
    }
}

class DefaultKotlinKernelConfigFactory(
    project: Project,
    private val kernelPorts: KernelPorts,
    notebookPath: Path,
    replCompilerMode: ReplCompilerMode,
): AbstractKotlinKernelConfigFactory(project, notebookPath, replCompilerMode) {
    override fun getKernelPorts(): KernelPorts = kernelPorts

    override fun getDebugPortOrNull(notebookPath: Path): Int? {
        return KotlinNotebookDebugSessionManager.getInstance(project).getByPath(notebookPath)?.provideFreshDebugPort()
    }
}

fun chooseJvmTargetForSnippets(project: Project): LanguageLevel? {
    val options = KotlinNotebookProjectOptionsProvider.getInstance(project)
    val selectedTarget = options.jvmTargetForSnippets
    val myMaxBytecodeVersion = maxBytecodeVersion

    if (selectedTarget != null) {
        return if (myMaxBytecodeVersion != null) selectedTarget.coerceAtMost(myMaxBytecodeVersion)
        else selectedTarget
    }

    val jdkVersion = options.jdk.getVersion(project)
    if (jdkVersion == null) return null
    if (myMaxBytecodeVersion == null) return null
    if (jdkVersion.maxLanguageLevel <= myMaxBytecodeVersion) return null

    return myMaxBytecodeVersion
}

private fun KotlinNotebookMavenArtifactsDownloader.getClasspathArtifacts(project: Project): List<File> {
    return try {
        downloadAndUnzipBlocking(KotlinNotebookMavenArtifacts.SCRIPT_CLASSPATH_SHADOWED_ZIP)
    } catch (e: Exception) {
        logger<KotlinNotebookMavenArtifactsDownloader>().warn("Unable to download artifacts zip", e)
        downloadArtifactBlocking(
            KotlinNotebookMavenArtifacts.SCRIPT_CLASSPATH_SHADOWED,
            project.selectedKernelVersionAsString
        )
    }
}
