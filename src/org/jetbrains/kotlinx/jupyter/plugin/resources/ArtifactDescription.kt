// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.resources

import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription

interface ArtifactDescription {
    val group: String
    val artifact: String
}

fun ArtifactDescription.toIntellijModelDescription() = RepositoryLibraryDescription.findDescription(group, artifact)
