// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.resources

import org.jetbrains.idea.maven.aether.ArtifactKind

object KotlinNotebookMavenArtifacts {
    private val artifactDescriptions = mutableListOf<ArtifactDescriptionWithKind>()
    private fun ArtifactDescriptionWithKind.add(): ArtifactDescriptionWithKind {
        artifactDescriptions.add(this)
        return this
    }

    val KERNEL_SHADOWED = jupyterKernelLibrary("kernel-shadowed").add()
    val SCRIPT_CLASSPATH_SHADOWED = jupyterKernelLibrary("script-classpath-shadowed").add()
    val SCRIPT_CLASSPATH_SHADOWED_SOURCES = jupyterKernelLibrary("script-classpath-shadowed", ArtifactKind.SOURCES).add()
    val IDE_CLASSPATH_SHADOWED = jupyterKernelLibrary("ide-classpath-shadowed").add()

    fun all(): List<ArtifactDescriptionWithKind> = artifactDescriptions
}
