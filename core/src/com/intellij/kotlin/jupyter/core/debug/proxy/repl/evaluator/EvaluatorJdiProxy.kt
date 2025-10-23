// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.proxy.repl.evaluator

import com.intellij.kotlin.jupyter.core.debug.proxy.JdiObjectReferenceProxy
import com.intellij.kotlin.jupyter.core.debug.proxy.notebook.state.VariableStateJdiProxy
import com.intellij.kotlin.jupyter.core.debug.util.getFieldValueByName
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import org.jetbrains.kotlinx.jupyter.repl.InternalEvaluator

interface EvaluatorJdiProxy : InternalEvaluator, JdiObjectReferenceProxy