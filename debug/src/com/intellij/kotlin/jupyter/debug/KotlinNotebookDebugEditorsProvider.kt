// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug

import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider

class KotlinNotebookDebugEditorsProvider : XDebuggerEditorsProvider() {
    override fun getFileType(): FileType {
        return JupyterFileType
    }
}