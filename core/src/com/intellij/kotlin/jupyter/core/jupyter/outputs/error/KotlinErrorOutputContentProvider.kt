// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.outputs.error

import com.intellij.jupyter.core.jupyter.editor.outputs.JupyterErrorOutputDataKey
import com.intellij.jupyter.core.jupyter.editor.outputs.error.ErrorOutputContentProvider
import org.jetbrains.kotlinx.jupyter.exceptions.CompositeReplException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplEvalRuntimeException
import org.jetbrains.kotlinx.jupyter.exceptions.ReplLibraryException

class KotlinErrorOutputContentProvider : ErrorOutputContentProvider {
    private val replExceptionTypesWithMeaningfulStacktrace = listOf(
        ReplEvalRuntimeException::class,
        ReplLibraryException::class,
        CompositeReplException::class,
    ).map { it.java.name }

    override fun getContent(outputDataKey: JupyterErrorOutputDataKey): List<String>? {
        val exceptionType = outputDataKey.exceptionType

        return when {
            !exceptionType.startsWith("org.jetbrains.kotlinx.jupyter") -> null
            replExceptionTypesWithMeaningfulStacktrace.contains(exceptionType) -> null
            else -> listOf(outputDataKey.exceptionValue)
        }
    }
}
