// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.error

import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.JupyterErrorOutputDataKey
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.error.ErrorOutputContentProvider

class KotlinErrorOutputContentProvider : ErrorOutputContentProvider {
    override fun getContent(outputDataKey: JupyterErrorOutputDataKey): List<String>? {
        return when {
            !outputDataKey.exceptionType.startsWith("org.jetbrains.kotlinx.jupyter") -> null
            outputDataKey.exceptionType == ReplEvalRuntimeException::class.java.name -> null
            else -> listOf(outputDataKey.exceptionValue)
        }
    }
}
