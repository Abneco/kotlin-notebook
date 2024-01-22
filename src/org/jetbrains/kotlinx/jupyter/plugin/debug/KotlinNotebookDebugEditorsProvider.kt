// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.debug

import com.intellij.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider

class KotlinNotebookDebugEditorsProvider : XDebuggerEditorsProvider() {
    override fun getFileType(): FileType {
        return JupyterFileType
    }
}