// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions.ActionText

private val isKernelProcessEmbeddingEnabled by registryFlag("kotlin.notebook.allow.embedded.kernel", false)
private val isKernelAttachedModeEnabled by registryFlag("kotlin.notebook.allow.attached.kernel", false)

val isKernelRunModeSelectionEnabled get() = isKernelProcessEmbeddingEnabled || isKernelAttachedModeEnabled

enum class KotlinNotebookSessionRunMode(@ActionText val title: String) {
    SEPARATE_PROCESS(KotlinNotebookBundle.message("action.KotlinNotebookEnableSeparateProcessMode.text")),
    IDE_PROCESS(KotlinNotebookBundle.message("action.KotlinNotebookEnableIdeProcessMode.text")),
    ATTACHED_PROCESS(KotlinNotebookBundle.message("action.KotlinNotebookEnableAttachedProcessMode.text"));

    companion object
}

val KotlinNotebookSessionRunMode.isAvailable get(): Boolean = when (this) {
    KotlinNotebookSessionRunMode.SEPARATE_PROCESS -> true
    KotlinNotebookSessionRunMode.IDE_PROCESS -> isKernelProcessEmbeddingEnabled
    KotlinNotebookSessionRunMode.ATTACHED_PROCESS -> isKernelAttachedModeEnabled
}

val KotlinNotebookSessionRunMode.Companion.DEFAULT get() = KotlinNotebookSessionRunMode.SEPARATE_PROCESS

fun BackedNotebookVirtualFile.getSessionRunMode(project: Project): KotlinNotebookSessionRunMode {
    val settings = KotlinNotebookPerFileSettingsCache.getInstance(project).getSettings(this)
    return settings.sessionRunMode
}
