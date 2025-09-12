// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.appearance

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.execution.toolwindow.KernelProcessToolWindowCoordinator
import com.intellij.jupyter.execution.toolwindow.KernelProcessToolWindowCoordinatorProvider
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.project.Project

class KotlinKernelProcessToolWindowCoordinatorProvider : KernelProcessToolWindowCoordinatorProvider {
    override fun canHandle(vfile: BackedNotebookVirtualFile): Boolean = vfile.isKotlinNotebook

    override fun create(project: Project, vfile: BackedNotebookVirtualFile): KernelProcessToolWindowCoordinator {
        return KotlinNotebookToolWindowCoordinator(project, vfile)
    }
}