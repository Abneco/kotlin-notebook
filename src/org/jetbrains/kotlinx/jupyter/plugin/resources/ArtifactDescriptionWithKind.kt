// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.resources

import org.jetbrains.idea.maven.aether.ArtifactKind

const val KOTLINX_GROUP = "org.jetbrains.kotlinx"

data class ArtifactDescriptionWithKind(
    override val group: String,
    override val artifact: String,
    val kind: ArtifactKind,
): ArtifactDescription

fun jupyterKernelLibrary(artifactNameSuffix: String, kind: ArtifactKind = ArtifactKind.ARTIFACT) = ArtifactDescriptionWithKind(
    KOTLINX_GROUP, "kotlin-jupyter-$artifactNameSuffix", kind)
