// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.debug

import com.intellij.openapi.fileTypes.FileType
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import org.jetbrains.plugins.notebooks.jupyter.JupyterFileType

class KJupyterDebugEditorsProvider : XDebuggerEditorsProvider() {
    override fun getFileType(): FileType {
        return JupyterFileType
    }
}