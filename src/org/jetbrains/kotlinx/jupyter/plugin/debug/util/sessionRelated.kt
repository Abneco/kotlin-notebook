// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.debug.util

import com.intellij.openapi.project.Project
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import com.intellij.notebooks.visualization.NotebookIntervalPointer

data class SessionRelatedInfo(
  var project: Project,
  var myNotebook: BackedNotebookVirtualFile,
  var lastCompiledCellId: Int = 1
) {
    fun updateWith(project: Project,
                   port: Int? = null,
                   cell: JupyterPsiCell? = null,
                   cellPointer: NotebookIntervalPointer? = null,
                   cellFileName: String? = null,
                   sessionPath: String? = null) {
        this.project = project
        this.cell = cell
        this.cellPointer = cellPointer
        this.cellFileName = cellFileName
        this.sessionPath = sessionPath
        this.debugPort = port
    }

    var debugPort: Int? = null
    var cell: JupyterPsiCell? = null
    var cellPointer: NotebookIntervalPointer? = null
    var cellFileName: String? = null
    var sessionPath: String? = myNotebook.file.path
}

