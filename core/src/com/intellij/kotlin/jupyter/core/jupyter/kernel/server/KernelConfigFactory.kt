// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.session.KernelStartupOptions
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.extensions.KernelDebugOptionsCustomizer
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
import org.jetbrains.kotlinx.jupyter.protocol.startup.ANY_HOST_NAME
import org.jetbrains.kotlinx.jupyter.protocol.startup.KERNEL_SIGNATURE_SCHEME
import org.jetbrains.kotlinx.jupyter.protocol.startup.KERNEL_TRANSPORT_SCHEME
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelJupyterParams
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParams
import java.nio.file.Path

interface KernelConfigFactory {
    fun create(): KernelConfig<KotlinKernelOwnParams>
}

abstract class AbstractKotlinKernelConfigFactory(
    startupOptions: KernelStartupOptions,
) : KernelConfigFactory {
    protected val project: Project = startupOptions.project
    protected val notebookPath: Path = startupOptions.notebookPath
    protected val replCompilerMode: ReplCompilerMode = startupOptions.replCompilerMode
    protected val extraCompilerArguments: List<String> = startupOptions.extraCompilerArguments

    final override fun create(): KernelConfig<KotlinKernelOwnParams> = KernelConfig(
        jupyterParams = KernelJupyterParams(
            host = getHost(),
            ports = getKernelPorts(),
            transport = KERNEL_TRANSPORT_SCHEME,
            signatureScheme = KERNEL_SIGNATURE_SCHEME,
            signatureKey = getSignature(),
        ),
        ownParams = KotlinKernelOwnParams(
            scriptClasspath = getClasspath().map { it.toFile() },
            // Don't try to resolve libraries against some local directory,
            // use only embedded or remote JSON library files
            homeDir = null,
            debugPort = getDebugPortOrNull(notebookPath),
            // Kernel provides an API for a user to learn in what environment the session is run
            // In particular, a client type is available via `notebook.jupyterClientType`
            // in both separate and embedded modes
            clientType = JupyterClientType.KOTLIN_NOTEBOOK.name,
            jvmTargetForSnippets = getJvmTargetForSnippets(project)?.toFeatureString(),
            replCompilerMode = replCompilerMode,
            extraCompilerArguments = extraCompilerArguments,
        ),
    )

    protected abstract fun getKernelPorts(): KernelPorts

    protected abstract fun getDebugPortOrNull(notebookPath: Path): Int?

    protected open fun getHost(): String = ANY_HOST_NAME
    protected open fun getSignature(): String {
        // Key doesn't matter as long as it's a local kernel
        return "x-x-x"
    }

    protected open fun getClasspath(): List<Path> {
        return KotlinNotebookMavenArtifactsDownloader.getInstance(project).getClasspathArtifacts(project)
    }

    protected open fun getJvmTargetForSnippets(project: Project): JavaVersion? {
        return chooseJvmTargetForSnippets(project)?.toJavaVersion()
    }
}

class DefaultKotlinKernelConfigFactory(
    startupOptions: KernelStartupOptions,
    private val kernelPorts: KernelPorts,
): AbstractKotlinKernelConfigFactory(startupOptions) {
    override fun getKernelPorts(): KernelPorts = kernelPorts

    override fun getDebugPortOrNull(notebookPath: Path): Int? {
        return KernelDebugOptionsCustomizer.getDebugPort(project, notebookPath)
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

private fun KotlinNotebookMavenArtifactsDownloader.getClasspathArtifacts(project: Project): List<Path> {
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
