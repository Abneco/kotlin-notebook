// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.resources

import org.jetbrains.idea.maven.aether.ArtifactKind

object KotlinNotebookMavenArtifacts {
    private val artifactDescriptions = mutableListOf<ArtifactDescriptionWithKind>()
    private fun ArtifactDescriptionWithKind.add(): ArtifactDescriptionWithKind {
        artifactDescriptions.add(this)
        return this
    }

    val KERNEL_SHADOWED: ArtifactDescriptionWithKind = jupyterKernelLibrary("kernel-shadowed").add()
    val SCRIPT_CLASSPATH_SHADOWED: ArtifactDescriptionWithKind = jupyterKernelLibrary("script-classpath-shadowed").add()
    val SCRIPT_CLASSPATH_SHADOWED_ZIP: ArtifactDescriptionWithKind = jupyterKernelLibrary("script-classpath-shadowed", ArtifactKind.ZIP).add()
    val IDE_CLASSPATH_SHADOWED: ArtifactDescriptionWithKind = jupyterKernelLibrary("ide-classpath-shadowed").add()
    val IDE_CLASSPATH_SHADOWED_SOURCES: ArtifactDescriptionWithKind = jupyterKernelLibrary("ide-classpath-shadowed", ArtifactKind.SOURCES).add()
    val EMBEDDED_KERNEL: ArtifactDescriptionWithKind = jupyterKernelLibrary("embeddable-kernel").add()

    fun all(): List<ArtifactDescriptionWithKind> = artifactDescriptions
}
