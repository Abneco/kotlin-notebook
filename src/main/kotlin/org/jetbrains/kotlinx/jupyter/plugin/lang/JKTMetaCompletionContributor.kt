package org.jetbrains.kotlinx.jupyter.plugin.lang

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.kotlinx.jupyter.common.ReplCommand
import org.jetbrains.kotlinx.jupyter.common.ReplEnum
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.plugin.psi.meta.JKTMetaTypes

class JKTMetaCompletionContributor : CompletionContributor() {
    private val magicLookups by lazy { ReplLineMagic.toLookupElements() }
    private val commandLookups by lazy { ReplCommand.toLookupElements() }

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        super.fillCompletionVariants(parameters, result)

        if (parameters.position.elementType == JKTMetaTypes.ID) {
            fillIdVariants(parameters.position, result)
        }
    }

    private fun fillIdVariants(element: PsiElement, result: CompletionResultSet) {
        val statementElement = element.parent
        val lookupElements = when (statementElement.elementType) {
            JKTMetaTypes.MAGIC_STATEMENT -> magicLookups
            JKTMetaTypes.COMMAND_STATEMENT -> commandLookups
            else -> emptyList()
        }

        result.addAllElements(lookupElements)
    }

    companion object {
        private fun ReplEnum<*>.toLookupElements(): List<LookupElement> {
            return this.codeInsightValues.map {
                LookupElementBuilder.create(it.name).withTypeText(it.type.name)
            }
        }
    }
}
