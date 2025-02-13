// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.pass.state

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.atomic.AtomicInteger

internal data class InjectedFileData(
    val notebookCellIndex: Int,
    val file: KtFile,
    val ktFileRange: TextRange,
    val injectionHost: PsiLanguageInjectionHost,
    val totalTokens: Int
) {
    val processedTokens = AtomicInteger(0)
}