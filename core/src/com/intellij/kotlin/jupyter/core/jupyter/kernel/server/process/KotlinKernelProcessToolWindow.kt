// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.kernel.server.process

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.jupyter.core.jupyter.server.ui.attachJupyterServerContentCloseListener
import com.intellij.kotlin.jupyter.core.jupyter.toolwindow.KotlinNotebookToolWindowSettings
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.ui.content.Content

/**
 * Class responsible for managing the setup of the tool window when running the kernel in a
 * separate process.
 */
class KotlinKernelProcessToolWindow(
  override val handler: SeparateProcessKotlinKernelRunnableHandler,
): KotlinNotebookToolWindowSettings() {
    override fun toolWindowContentCreated(newContent: Content) {
        super.toolWindowContentCreated(newContent)
        attachJupyterServerContentCloseListener(
            newContent,
            project,
            KotlinNotebookBundle.message("kotlin.jupyter.toolbar.session.name"),
            handler.process
        )
   }

    override fun consoleWindowCreated(console: ConsoleViewImpl) {
        console.attachToProcess(handler.process)
    }

    override fun shouldShowVariablesView(): Boolean = true
}
