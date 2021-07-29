package org.jetbrains.kotlinx.jupyter.plugin.lang.codeInsight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlinx.jupyter.plugin.lang.psi.JKTMetaStatement
import org.jetbrains.kotlinx.jupyter.plugin.lang.util.replEnum

class JKTMetaAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val statement = element as? JKTMetaStatement ?: return
        val enum = statement.replEnum ?: return
        val description = enum.valueOfOrNull(statement.getStatementId().text)?.description ?: return
        holder
            .newAnnotation(HighlightSeverity.INFORMATION, description)
            .tooltip(description)
            .range(TextRange(statement.startOffset, statement.getNewlineOrEof().startOffset))
            .create()
    }
}
