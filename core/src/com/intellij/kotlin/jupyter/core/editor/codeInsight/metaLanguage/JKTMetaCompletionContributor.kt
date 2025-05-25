// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight.metaLanguage

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.kotlin.jupyter.core.logging.notebookLogger
import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.util.startOffset
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlinx.jupyter.common.ReplCommand
import org.jetbrains.kotlinx.jupyter.common.ReplEnum
import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.libraries.ResourceLibraryDescriptorsProvider
import kotlin.time.Duration.Companion.seconds

class JKTMetaCompletionContributor : CompletionContributor(), DumbAware {
    private val magicsCompleter = KotlinNotebookMagicsCompleter(
        ResourceLibraryDescriptorsProvider(DefaultKernelLoggerFactory)
    )

    @RequiresReadLock
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
        KotlinNotebookPluginScope.global.invokeAndWait(20.seconds, action = {
            magicsCompleter.process(statementText, cursor, result)
        }) { t ->
            if (t is ProcessCanceledException) {
                throw t
            }
            LOG.warn("Error while completing variants for $statementText at position:$cursor", t)
        }
    }

    companion object {
        private val LOG = notebookLogger()
    }
}
