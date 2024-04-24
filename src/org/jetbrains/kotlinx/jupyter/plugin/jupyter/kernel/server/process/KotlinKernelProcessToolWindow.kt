// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow.KotlinNotebookToolWindowRunMode
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.plugins.notebooks.jupyter.server.ui.attachJupyterServerContentCloseListener
import java.nio.file.Path

/**
 * Class responsible for managing the setup of the tool window when running the kernel in a
 * separate process.
 */
class KotlinKernelProcessToolWindow(
    project: Project,
    notebookPath: Path,
    override val handler: KotlinKernelProcessHandler,
): KotlinNotebookToolWindowRunMode(project, notebookPath) {
    override fun makeToolWindowClosableWhenStoppingKernel(newContent: Content) {
        super.makeToolWindowClosableWhenStoppingKernel(newContent)
        attachJupyterServerContentCloseListener(
            newContent,
            project,
            KotlinNotebookBundle.message("kotlin.jupyter.toolbar.session.name"),
            handler
        )
   }

    override fun consoleWindowCreated(console: ConsoleViewImpl) {
        console.attachToProcess(handler)
    }

    override fun shouldShowVariablesView(): Boolean = true
}
