package org.jetbrains.kotlinx.jupyter.plugin.lang.codeInsight

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.kotlinx.jupyter.common.ReplCommand
import org.jetbrains.kotlinx.jupyter.common.ReplEnum
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.lang.psi.JKTMetaStatement
import org.jetbrains.kotlinx.jupyter.plugin.lang.util.replEnum
import org.jetbrains.kotlinx.jupyter.plugin.psi.meta.JKTMetaStatementId
import org.jetbrains.kotlinx.jupyter.plugin.psi.meta.JKTMetaTypes
import org.jetbrains.plugins.notebooks.jupyter.JupyterLanguage

class JKTMetaCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        super.fillCompletionVariants(parameters, result)

        val position = parameters.position
        if (position.elementType == JKTMetaTypes.ID) {
            fillIdVariants(position, result, true)
        } else {
            val original = parameters.originalPosition ?: return
            if (original.containingFile?.virtualFile?.isKotlinNotebook != true) return

            fillIdVariants(original, result, false)
            result.stopHere()
        }
    }

    private fun fillIdVariants(element: PsiElement, result: CompletionResultSet, isFromJKTMeta: Boolean, stopAfter: Boolean = true) {
        val enum = (if (!isFromJKTMeta) element.findMetaStatementEnumInJupyter()
            else element.findMetaStatement()?.replEnum) ?: return
        val lookupElements = enum.toLookupElements()
        result.addAllElements(lookupElements)
        result.restartCompletionOnAnyPrefixChange()
        if (stopAfter) {
            result.stopHere()
        }
    }

    companion object {
        private const val metaIDCommand = ':'
        private const val metaMagicCommand = '%'

        fun PsiElement.findMetaStatementEnumInJupyter(): ReplEnum<*>? {
            if (language != JupyterLanguage) return null
            // consider only leaf nodes to get text from
            if (this !is LeafPsiElement) return null
            if (text.length > 20) return null
            return when (text[0]) {
              metaIDCommand -> {
                  ReplCommand
              }
              metaMagicCommand -> ReplLineMagic
              else -> null
            }
        }
        fun PsiElement.findMetaStatement(): JKTMetaStatement? {
            if (this is JKTMetaStatement) return this
            if (this is JKTMetaStatementId || this.parent is JKTMetaStatementId) return this.parent.findMetaStatement()
            return null
        }

        private fun ReplEnum<*>.toLookupElements(): List<LookupElement> {
            return this.codeInsightValues.map {
                LookupElementBuilder.create(it.name).withTypeText(it.type.name)
            }
        }
    }
}
