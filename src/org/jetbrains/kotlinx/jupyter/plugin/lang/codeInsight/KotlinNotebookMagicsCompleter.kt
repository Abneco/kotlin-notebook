// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.lang.codeInsight

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.HttpRequests
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorsProvider
import org.jetbrains.kotlinx.jupyter.magics.AbstractCompletionMagicsProcessor
import java.io.IOException

class KotlinNotebookMagicsCompleter(
    descriptorsProvider: LibraryDescriptorsProvider
): AbstractCompletionMagicsProcessor<LookupElement>(descriptorsProvider) {
    override fun key(variant: LookupElement): String {
        return variant.lookupString
    }

    override fun variant(text: String, icon: String): LookupElement {
        return LookupElementBuilder.create(text).withTypeText(icon)
    }

    override fun getHttpResponseText(url: String): String? {
        return try {
            HttpRequests.request(url).readString()
        } catch (e: IOException) {
            logger<KotlinNotebookMagicsCompleter>().warn("Magic completion request failed")
            null
        }
    }

    fun process(metaStatement: String, cursor: Int, result: CompletionResultSet) {
        if (metaStatement.isEmpty() || metaStatement[0] != '%') return
        val handler = Handler()
        handler.handle(metaStatement.substring(1), if (cursor > 0) cursor - 1 else cursor)
        result.addAllElements(handler.completions)
    }
}