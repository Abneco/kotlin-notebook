// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.toolwindow

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.jupyter.kernel.server.KotlinKernelRunnableHandler
import com.intellij.kotlin.jupyter.core.util.findNotebookVirtualFileOrNull
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import java.nio.file.Path

/**
 * Abstract class for handling the different UI logic for the Kotlin Notebook
 * Tool window depending on if the kernel is either running inside the IDE
 * process or in a separate one.
 */
abstract class KotlinNotebookToolWindowSettings {
    /**
     * Handler responsible for acting on kernel callbacks
     */
    abstract val handler: KotlinKernelRunnableHandler

    val project: Project get() = handler.project
    val notebookPath: Path get() = handler.notebookPath
    
    fun notebookVirtualFile(): BackedNotebookVirtualFile {
        return notebookPath.findNotebookVirtualFileOrNull() ?: error("File not found: $notebookPath")
    }

    open fun toolWindowContentCreated(newContent: Content) {
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
