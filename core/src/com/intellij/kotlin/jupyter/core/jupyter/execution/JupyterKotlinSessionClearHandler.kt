// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.execution

import com.intellij.jupyter.core.executor.JupyterExecutionListener
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterNotebookSession
import com.intellij.kotlin.jupyter.core.editor.highlighting.utils.resetSessionMetaInformation

class JupyterKotlinSessionClearHandler : JupyterExecutionListener {
    override suspend fun sessionDeleted(session: JupyterNotebookSession) {
        resetSessionMetaInformation(session.project, session.virtualFile)
    }
}