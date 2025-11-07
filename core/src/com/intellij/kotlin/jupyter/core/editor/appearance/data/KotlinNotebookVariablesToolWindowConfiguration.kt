// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.appearance.data

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe

/**
 * Data set which is directed from the core plugin part
 * to configure and build a Variables tool window
 */
data class KotlinNotebookVariablesToolWindowConfiguration(
    val project: Project,
    val virtualFile: BackedNotebookVirtualFile,
    val uiRunnerLayoutUi: RunnerLayoutUi,
    val helpId: String,
    @NlsSafe val title: String
)
