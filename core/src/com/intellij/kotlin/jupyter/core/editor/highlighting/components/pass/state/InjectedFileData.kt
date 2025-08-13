// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.components.pass.state

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.atomic.AtomicInteger

/**
 * Is used to track the progress of [KtFile] highlighting, e.g.,
 * the total number of tokens in the PSI-tree and the number of tokens being actually applied
 * to the [com.intellij.openapi.editor.markup.MarkupModel].
 *
 * [notebookCellIndex] The index of the notebook cell associated with this injected file.
 * [file] Actual [KtFile].
 * [ktFileRange] The range of the injected file's content in the host document.
 * [injectionHost] The host PSI element for the injection.
 * [totalTokens] The total number of syntax tokens in the injected file.
 * [appliedTokens] An atomic counter to track the number of tokens applied to the [com.intellij.openapi.editor.Editor].
 */
internal data class InjectedFileData(
    val notebookCellIndex: Int,
    val file: KtFile,
    val ktFileRange: TextRange,
    val injectionHost: PsiLanguageInjectionHost,
    val totalTokens: Int
) {
    val appliedTokens = AtomicInteger(0)
}