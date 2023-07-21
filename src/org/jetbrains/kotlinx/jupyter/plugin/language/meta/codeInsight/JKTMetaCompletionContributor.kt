package org.jetbrains.kotlinx.jupyter.plugin.language.meta.codeInsight

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlinx.jupyter.common.ReplCommand
import org.jetbrains.kotlinx.jupyter.common.ReplEnum
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.libraries.ResourceLibraryDescriptorsProvider
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.psi.JKTMetaStatement
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.util.replEnum
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class JKTMetaCompletionContributor : CompletionContributor() {
    private val magicsCompleter = KotlinNotebookMagicsCompleter(ResourceLibraryDescriptorsProvider())

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        super.fillCompletionVariants(parameters, result)

        val position = parameters.position
        val metaStatement = position.findMetaStatement() ?: return

        when(val replEnum = metaStatement.replEnum) {
            ReplCommand -> fillIdVariants(result, replEnum)
            ReplLineMagic -> fillMagicVariants(
                metaStatement.text,
                parameters.offset - metaStatement.startOffset,
                result
            )
        }
    }

    private fun fillIdVariants(result: CompletionResultSet, enum: ReplEnum<*>?) {
        enum ?: return
        val lookupElements = enum.toLookupElements()
        result.addAllElements(lookupElements)
        result.restartCompletionOnAnyPrefixChange()
        result.stopHere()
    }

    private fun fillMagicVariants(statementText: String, cursor: Int, result: CompletionResultSet) {
        val replyNotifier = CountDownLatch(1)
        val future = ApplicationManager.getApplication().executeOnPooledThread {
            try {
                magicsCompleter.process(statementText, cursor, result)
            } finally {
                replyNotifier.countDown()
            }
        }
        val completionEnded = replyNotifier.await(20, TimeUnit.SECONDS)
        if (!completionEnded) {
            future.cancel(true)
        }
    }

    companion object {
        fun PsiElement.findMetaStatement(): JKTMetaStatement? {
            return findParentOfType<JKTMetaStatement>(strict = false)
        }

        private fun ReplEnum<*>.toLookupElements(): List<LookupElement> {
            return this.codeInsightValues.map {
                LookupElementBuilder.create(it.name).withTypeText(it.type.name)
            }
        }
    }
}
