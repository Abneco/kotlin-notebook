// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle

val isKernelProcessEmbeddingEnabled by registryFlag("kotlin.notebook.allow.embedded.kernel", false)

enum class KotlinNotebookSessionRunMode(
    @NlsContexts.Label val description: String
) {
    SEPARATE_PROCESS(KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.mode.separate.process")),
    IDE_PROCESS(KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.mode.ide.process"));

    companion object
}

val KotlinNotebookSessionRunMode.Companion.DEFAULT get() = KotlinNotebookSessionRunMode.SEPARATE_PROCESS
