// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.build

import org.jetbrains.intellij.build.buildPlugin

internal object KotlinNotebookPluginBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) = buildPlugin(
    "intellij.notebooks.plugin",
    "intellij.jupyter.plugin",
    "intellij.kotlin.jupyter.plugin",
  ) {
    skipProprietaryBuildTools()
    options { disableEmbeddedFrontend() }
    notifyArtifactBuilt()
  }
}
