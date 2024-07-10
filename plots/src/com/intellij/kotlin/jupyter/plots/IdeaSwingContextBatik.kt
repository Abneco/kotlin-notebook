// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.letsPlot.awt.plot.component.ApplicationContext

object IdeaSwingContextBatik : ApplicationContext {
    override fun runWriteAction(action: Runnable) {
        action.run()
    }

    override fun invokeLater(action: Runnable, expared: () -> Boolean) {
        ApplicationManager.getApplication().invokeLater(action) { expared() }
    }

    val IDEA_EDT_EXECUTOR = { action: () -> Unit ->
        ApplicationManager.getApplication().invokeLater(action)
    }
}
