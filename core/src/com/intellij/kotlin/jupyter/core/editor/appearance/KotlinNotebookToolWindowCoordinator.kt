// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.appearance

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.execution.kernel.KernelProcessAttachable
import com.intellij.jupyter.execution.toolwindow.KernelProcessToolWindowCoordinator
import com.intellij.kotlin.jupyter.core.editor.appearance.data.KotlinNotebookVariablesToolWindowConfiguration
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.util.toAbsolutePath
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content

internal class KotlinNotebookToolWindowCoordinator(
    project: Project,
    vfile: BackedNotebookVirtualFile
): KernelProcessToolWindowCoordinator(project, vfile) {
    override fun buildHelpId(vf: BackedNotebookVirtualFile): String {
        return KOTLIN_NOTEBOOK_RUNNER_ID + vf.file.toAbsolutePath()
    }

    override fun createVariablesView(layoutUi: RunnerLayoutUi, handler: KernelProcessAttachable): Content? {
        return KotlinNotebookToolVariablesUiContentProvider.createVariablesViewContent(
            KotlinNotebookVariablesToolWindowConfiguration(
                project,
                vfile,
                layoutUi,
                buildHelpId(vfile),
                KotlinNotebookBundle.message("kotlin.jupyter.toolbar.tabs.variables")
            )
        )
    }

    companion object {
        internal const val KOTLIN_NOTEBOOK_RUNNER_ID = "Kotlin Notebook Runner"
    }
}