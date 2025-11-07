// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.repl.evaluator

import com.intellij.kotlin.jupyter.debug.proxy.JdiObjectReferenceProxy
import org.jetbrains.kotlinx.jupyter.repl.InternalEvaluator

interface EvaluatorJdiProxy : InternalEvaluator, JdiObjectReferenceProxy