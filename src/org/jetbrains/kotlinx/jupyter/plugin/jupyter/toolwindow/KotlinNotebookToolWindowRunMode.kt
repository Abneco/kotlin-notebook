// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.toolwindow

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelEvent
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelListener
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.kernel.server.KotlinKernelRunnableHandler
import org.jetbrains.kotlinx.jupyter.plugin.util.findNotebookVirtualFileOrNull
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import java.nio.file.Path

/**
 * Abstract class for handling the different UI logic for the Kotlin Notebook
 * Tool window depending on if the kernel is either running inside the
 * process or in a separate one.
 */
abstract class KotlinNotebookToolWindowRunMode(
    val project: Project,
    val notebookPath: Path
) {
    /**
     * Handler responsible for acting on kernel callbacks
     */
    abstract val handler: KotlinKernelRunnableHandler
    
    fun notebookVirtualFile(): BackedNotebookVirtualFile {
        return notebookPath.findNotebookVirtualFileOrNull() ?: error("File not found: $notebookPath")
    }
    /**
     * When the kernel is stopped, the file panel in the Kotlin Notebook tool window
     * should be made closable. Otherwise, it should always be present.
     */
    open fun makeToolWindowClosableWhenStoppingKernel(newContent: Content) {
        handler.addKernelListener(object : KotlinKernelListener {
            override fun kernelTerminated(event: KotlinKernelEvent) {
                if (!newContent.isValid || project.isDisposed || !project.isInitialized) return
                runInEdt {
                    newContent.isCloseable = true
                }
            }
        })
    }

    /**
     * Called after the console window has been created and is ready to be used.
     */
    abstract fun consoleWindowCreated(console: ConsoleViewImpl)

    /**
     *  Whether to show the variable window.
     */
    abstract fun shouldShowVariablesView(): Boolean
}
