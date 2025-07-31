// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server

import com.intellij.jupyter.core.jupyter.connections.session.SessionStartupOptions
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookApplicationOptions
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode

val SessionStartupOptions.replCompilerMode: ReplCompilerMode
    get() = KotlinNotebookApplicationOptions.get().replCompilerMode

val SessionStartupOptions.extraCompilerArguments: List<String>
    get() = KotlinNotebookProjectOptionsProvider.getInstance(project).extraCompilerArguments.toList()
