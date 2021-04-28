package org.jetbrains.kotlinx.jupyter.plugin.lang.codeInsight

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.kotlinx.jupyter.common.ReplEnum
import org.jetbrains.kotlinx.jupyter.plugin.lang.psi.JKTMetaStatement
import org.jetbrains.kotlinx.jupyter.plugin.lang.util.replEnum
import org.jetbrains.kotlinx.jupyter.plugin.psi.meta.JKTMetaStatementId
import org.jetbrains.kotlinx.jupyter.plugin.psi.meta.JKTMetaTypes

class JKTMetaCompletionContributor : CompletionContributor() {
    private val cache: HashMap<ReplEnum<*>, List<LookupElement>> = hashMapOf()

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        super.fillCompletionVariants(parameters, result)

        if (parameters.position.elementType == JKTMetaTypes.ID) {
            fillIdVariants(parameters.position, result)
        }
    }

    private fun fillIdVariants(element: PsiElement, result: CompletionResultSet) {
        val enum = element.findMetaStatement()?.replEnum ?: return
        val lookupElements = cache.getOrPut(enum) { enum.toLookupElements() }
        result.addAllElements(lookupElements)
        result.restartCompletionOnAnyPrefixChange()
    }

    companion object {
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
