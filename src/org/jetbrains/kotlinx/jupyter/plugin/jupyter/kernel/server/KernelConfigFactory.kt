// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.lang.JavaVersion
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifacts
import org.jetbrains.kotlinx.jupyter.plugin.resources.KotlinNotebookMavenArtifactsDownloader
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.getSelectedKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.settings.ui.maxBytecodeVersion
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.KernelPorts
import java.io.File

interface KernelConfigFactory {
    fun create(): KernelConfig
}

abstract class AbstractKotlinKernelConfigFactory(
    protected val project: Project
) : KernelConfigFactory {
    final override fun create(): KernelConfig = KernelConfig(
        getKernelPorts(),
        "tcp",
        "HmacSHA256",
        "x-x-x",
        getClasspath(),
        null,
        null,
        "kotlin_notebook",
        jvmTargetForSnippets = getJvmTargetForSnippets(project)?.toFeatureString(),
    )

    protected abstract fun getKernelPorts(): KernelPorts

    protected open fun getClasspath(): List<File> {
        return KotlinNotebookMavenArtifactsDownloader.getInstance(project).getClasspathArtifacts(project)
    }

    protected open fun getJvmTargetForSnippets(project: Project): JavaVersion? {
        return chooseJvmTargetForSnippets(project)?.toJavaVersion()
    }
}

class DefaultKotlinKernelConfigFactory(
    project: Project,
    private val kernelPorts: KernelPorts
): AbstractKotlinKernelConfigFactory(project) {
    override fun getKernelPorts() = kernelPorts
}

private fun chooseJvmTargetForSnippets(project: Project): LanguageLevel? {
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
            getSelectedKernelVersion(project)
        )
    }
}
