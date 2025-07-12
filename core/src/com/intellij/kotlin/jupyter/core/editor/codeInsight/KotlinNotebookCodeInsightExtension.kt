// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight

import com.intellij.kotlin.jupyter.core.debug.util.KOTLIN_NOTEBOOK_BASE_CLASS_SUFFIX
import com.intellij.kotlin.jupyter.core.util.isInsideKotlinNotebookCodeCell
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebookCodeCell
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.KotlinIdeCodeInsightExtension

private const val classSuffixWithDot = "$KOTLIN_NOTEBOOK_BASE_CLASS_SUFFIX."
private val typeNameBounds = setOf('<', '>', '.')

class KotlinNotebookCodeInsightExtension: KotlinIdeCodeInsightExtension {
    override fun chooseDanglingFileResolutionMode(contextElement: PsiElement): KaDanglingFileResolutionMode? {
        if (!contextElement.isInsideKotlinNotebookCodeCell) return null

        return KaDanglingFileResolutionMode.IGNORE_SELF
    }

    override fun modifyRenderedType(contextElement: PsiElement, renderedType: String): String? {
        if (!contextElement.isInsideKotlinNotebookCodeCell) return null

        var resultType = renderedType
        while (true) {
            val suffixIndex = resultType.indexOf(classSuffixWithDot)
            if (suffixIndex == -1) break
            val boundIndex = resultType
                .take(suffixIndex)
                .indexOfLast { it in typeNameBounds }
            resultType = resultType.removeRange(boundIndex + 1, suffixIndex + classSuffixWithDot.length)
        }
        return resultType
    }

    override fun shouldAddDeclarationBeforeUsage(container: PsiElement): Boolean? {
        if (!container.isKotlinNotebookCodeCell) return null

        // In K2 REPL model, notebook cells are somewhat similar to function bodies,
        // so we should add declarations before their usage
        return true
    }
}
