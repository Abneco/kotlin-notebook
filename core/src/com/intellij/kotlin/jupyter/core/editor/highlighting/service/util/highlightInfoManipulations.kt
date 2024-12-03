// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.service.util

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Errors

private val shadowedSymbolSeverity = HighlightInfo.convertSeverity(HighlightSeverity.INFORMATION)

fun convertToShadowedDeclaration(psiElement: PsiElement, factoryName: String): HighlightInfo {
    return HighlightInfo.newHighlightInfo(shadowedSymbolSeverity)
        .range(psiElement.textRange)
        .textAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES)
        .needsUpdateOnTyping(false)
        .group(0)
        .fillInProperDescription(factoryName)
        .createUnconditionally()
}

private fun HighlightInfo.Builder.fillInProperDescription(factoryName: String): HighlightInfo.Builder {
    return if (factoryName == Errors.UNRESOLVED_REFERENCE.name) {
        val unresolvedMessage = KotlinNotebookBundle.message("kotlin.jupyter.highlighting.symbols.styles.shadowed.unresolved.description")
        this.description(
            unresolvedMessage
        ).unescapedToolTip(
            unresolvedMessage
        )
    }
    else {
        this.escapedToolTip(
            KotlinNotebookBundle.message("kotlin.jupyter.highlighting.symbols.styles.shadowed.error.description")
        )
    }
}