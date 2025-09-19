// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.cells

import com.intellij.jupyter.core.jupyter.nbformat.JupyterCell
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell

/**
 * Represents meta info about executed cell.
 *
 * @see [com.intellij.kotlin.jupyter.core.jupyter.execution.KotlinNotebookCellExecutionCallback]
 */
data class ExecutedCellData(
    val cellIndex: Int,
    val psiCell: JupyterPsiCell?,
    val notebookCell: JupyterCell?
)
