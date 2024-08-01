// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.resources

import com.intellij.jarRepository.RemoteRepositoryDescription

private val INTELLIJ_DEPS_REPO = RemoteRepositoryDescription(
    "intellij-dependencies",
    "Intellij Dependencies",
    "https://packages.jetbrains.team/maven/p/ij/intellij-dependencies",
)

val defaultRemoteArtifactsRepositories = listOf(
    RemoteRepositoryDescription.MAVEN_CENTRAL,
    // INTELLIJ_DEPS_REPO,
)
