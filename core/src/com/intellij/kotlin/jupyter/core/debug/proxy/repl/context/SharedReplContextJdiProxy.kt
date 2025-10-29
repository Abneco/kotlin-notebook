// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.repl.context

import com.intellij.kotlin.jupyter.core.debug.proxy.JdiFieldAccessPath
import com.intellij.kotlin.jupyter.core.debug.proxy.JdiObjectReferenceProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.repl.evaluator.EvaluatorJdiProxy
import org.jetbrains.kotlinx.jupyter.api.FieldsProcessor
import org.jetbrains.kotlinx.jupyter.magics.CompoundCodePreprocessor

/**
 * This class mimics the [org.jetbrains.kotlinx.jupyter.repl.SharedReplContext] API,
 * so that it's possible to use it as a [java.lang.reflect.Proxy]
 */
interface SharedReplContextJdiProxy : JdiObjectReferenceProxy {
    @get:JdiFieldAccessPath("evaluator")
    val evaluator: EvaluatorJdiProxy

    val fieldsProcessor: FieldsProcessor
    val codePreprocessor: CompoundCodePreprocessor
}