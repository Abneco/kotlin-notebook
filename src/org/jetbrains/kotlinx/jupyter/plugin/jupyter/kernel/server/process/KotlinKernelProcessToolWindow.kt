// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.process

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.ui.content.Content
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow.KotlinNotebookToolWindowSettings
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import com.intellij.jupyter.core.jupyter.server.ui.attachJupyterServerContentCloseListener

/**
 * Class responsible for managing the setup of the tool window when running the kernel in a
 * separate process.
 */
class KotlinKernelProcessToolWindow(
    override val handler: KotlinKernelProcessHandler,
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
