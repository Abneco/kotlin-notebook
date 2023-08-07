package org.jetbrains.kotlinx.jupyter.plugin.editor.codeInsight.metaLanguage

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.psi.JKTMetaStatement

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
