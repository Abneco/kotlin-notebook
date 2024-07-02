// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.embedded

import com.intellij.execution.impl.ConsoleViewImpl
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow.KotlinNotebookToolWindowSettings

/**
 * Class responsible for managing the setup of the tool window when running the kernel in
 * the same process as IDEA.
 */
class EmbeddedProcessToolWindow(
    override val handler: EmbeddedKernelRunnableHandler,
): KotlinNotebookToolWindowSettings() {

    override fun consoleWindowCreated(console: ConsoleViewImpl) {
        handler.loggerFactory.consoleView = console
    }

    override fun shouldShowVariablesView(): Boolean = false
}
