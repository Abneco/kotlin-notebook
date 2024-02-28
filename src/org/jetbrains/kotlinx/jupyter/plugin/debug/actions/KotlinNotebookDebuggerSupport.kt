// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.actions

import com.intellij.xdebugger.impl.XDebuggerSupport
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import org.jetbrains.kotlinx.jupyter.plugin.debug.actions.evaluate.NotebookSilentEvaluateActionHandler

/**
 * Delegates handling of some of XDebuggerAction to implementations suitable for a particular cases.
 * If we want to have features like ExpressionEvaluation, showing Referencing Objects, etc.
 * without stopping Kernel itself.
 *
 * Even though [XDebuggerSupport] uses class marked as @Deprecated, there is no replacement for now.
 */
class KotlinNotebookDebuggerSupport : XDebuggerSupport() {
    private val evaluateSilentlyHandler = NotebookSilentEvaluateActionHandler()

    override fun getEvaluateHandler(): DebuggerActionHandler {
        return evaluateSilentlyHandler
    }
}