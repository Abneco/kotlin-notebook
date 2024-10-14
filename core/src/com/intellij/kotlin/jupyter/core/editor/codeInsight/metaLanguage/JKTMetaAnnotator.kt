// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight.metaLanguage

import com.intellij.kotlin.jupyter.core.language.meta.psi.JKTMetaStatement
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class JKTMetaAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val statement = element as? JKTMetaStatement ?: return
        val enum = statement.replEnum ?: return
        val insightValue = enum.valueOfOrNull(statement.getStatementId().text) ?: return
        // TODO: to be addressed with i18n of ReplLineMagic / ReplCommand
        @Suppress("HardCodedStringLiteral")
        val description =
            KotlinNotebookBundle.messageWithDefaultValue("jkt.meta.description.${insightValue.name}", insightValue.description)
        holder
            .newAnnotation(HighlightSeverity.INFORMATION, description)
            .tooltip(description)
            .range(TextRange(statement.startOffset, statement.getNewlineOrEof().startOffset))
            .create()
    }
}
