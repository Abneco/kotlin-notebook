// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.highlighting.service.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.atomic.AtomicInteger


data class InjectedFileData(
    val notebookCellIndex: Int,
    val file: KtFile,
    val ktFileRange: TextRange,
    val injectionHost: PsiLanguageInjectionHost,
    val totalTokens: Int
) {
    val processedTokens = AtomicInteger(0)
}