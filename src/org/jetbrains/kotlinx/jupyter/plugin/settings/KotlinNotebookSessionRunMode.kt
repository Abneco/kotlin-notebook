// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle

private val isKernelProcessEmbeddingEnabled by registryFlag("kotlin.notebook.allow.embedded.kernel", false)
private val isKernelAttachedModeEnabled by registryFlag("kotlin.notebook.allow.attached.kernel", false)

val isKernelRunModeSelectionEnabled get() = isKernelProcessEmbeddingEnabled || isKernelAttachedModeEnabled

enum class KotlinNotebookSessionRunMode(
    @NlsContexts.Label val description: String
) {
    SEPARATE_PROCESS(KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.mode.separate.process")),
    IDE_PROCESS(KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.mode.ide.process")),
    ATTACHED_PROCESS(KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.mode.attached.process"));

    companion object
}

val KotlinNotebookSessionRunMode.isAvailable get(): Boolean = when (this) {
    KotlinNotebookSessionRunMode.SEPARATE_PROCESS -> true
    KotlinNotebookSessionRunMode.IDE_PROCESS -> isKernelProcessEmbeddingEnabled
    KotlinNotebookSessionRunMode.ATTACHED_PROCESS -> isKernelAttachedModeEnabled
}

val KotlinNotebookSessionRunMode.Companion.DEFAULT get() = KotlinNotebookSessionRunMode.SEPARATE_PROCESS
