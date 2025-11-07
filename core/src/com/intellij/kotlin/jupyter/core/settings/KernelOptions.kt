// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

internal val replCompilerModeSelectorEnabled: Boolean by registryFlag("kotlin.notebook.replCompilerMode.enabled", false)

val compilerPluginsEnabled: Boolean by registryFlag("kotlin.notebook.compilerPlugins.enabled", false)

internal val projectWideExtraCompilerArgumentsSelectionEnabled: Boolean by registryFlag("kotlin.notebook.projectWideExtraCompilerArguments.enabled", false)