// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.outputs.plots

import com.intellij.openapi.application.ApplicationManager
import jetbrains.datalore.vis.swing.ApplicationContext

object IdeaSwingContextBatik : ApplicationContext {
    override fun runWriteAction(action: Runnable) {
        ApplicationManager.getApplication().runWriteAction(action)
    }

    override fun invokeLater(action: Runnable, expared: () -> Boolean) {
        ApplicationManager.getApplication().invokeLater(action) { expared() }
    }

    val IDEA_EDT_EXECUTOR = { action: () -> Unit ->
        ApplicationManager.getApplication().invokeLater(action)
    }
}
