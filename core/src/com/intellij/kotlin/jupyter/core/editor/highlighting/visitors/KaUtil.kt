// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.visitors

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.diagnostics.Severity

/**
 * This is an intermediate class to represent diagnostics data
 * as there is no way to invoke analysis with full checks via
 * [KaDiagnosticProvider] directly.
 */
data class KaDiagnosticData(
    val psiElement: PsiElement,
    val severity: KaSeverity,
    val factoryName: String,
)

fun Severity.toKaSeverity(): KaSeverity {
    return when (this) {
        Severity.INFO -> KaSeverity.INFO
        Severity.ERROR -> KaSeverity.ERROR
        Severity.WARNING -> KaSeverity.WARNING
    }
}