// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.inject

import com.intellij.kotlin.jupyter.core.scriptingSupport.NotebookExtraLanguage
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlinx.jupyter.magics.AbstractMagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.NoopMagicsHandler
import org.jetbrains.plugins.notebooks.psi.jupyter.lexer.JupyterNotebookCellHeader.CELL_MARKER
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterSource

private val magicsProcessor = MagicsProcessor(
    handler = NoopMagicsHandler,
    parseOutCellMarker = true
)

/**
 * Returns code and magic ranges of the given [cell] with the contracts specified in
 * [CellRanges] class documentation
 */
fun getCellRanges(cell: JupyterPsiCell): CellRanges {
    val code = getCellCode(cell)
    if (looksLikeReplCommand(code)) return CellRanges(
        codeRanges = emptyMap(),
        magicRanges = listOf(TextRange(0, cell.textLength)),
    )

    val text = cell.text
    val magicIntervals = magicsProcessor.magicsIntervals(text)

    val codeRanges = getCodeRangesByLanguage(text, magicIntervals)
        .groupBy(
            { it.languageInfo },
            { it.interval }
        )
    val magicRanges = magicIntervals
        .filterNot { text.substring(it.from, it.to).startsWith(CELL_MARKER) }
        .map { TextRange(it.from, it.to) }
        .toList()

    return CellRanges(codeRanges, magicRanges)
}

private fun getCellCode(cell: PsiElement): String {
    val sourceElement = PsiTreeUtil.getChildOfType(cell, JupyterSource::class.java)
    val source = sourceElement?.text.orEmpty()
    return source.trimStart()
}

private fun getCodeRangesByLanguage(
    code: String,
    magicsIntervals: Sequence<CodeInterval>,
): Sequence<IntervalWithLanguage> = sequence {
    var codeStart = 0
    var languageInfo: NotebookExtraLanguage? = null
    var codeInserted = false

    suspend fun SequenceScope<IntervalWithLanguage>.yieldCode(from: Int, to: Int) {
        yield(
            IntervalWithLanguage(
                TextRange(from, to),
                languageInfo,
            )
        )
        codeInserted = true
    }

    for (interval in magicsIntervals) {
        if (codeStart != interval.from) {
            yieldCode(codeStart, interval.from)
        }
        codeStart = interval.to

        // If current magic corresponds to a language, it should be used to highlight the next code fragment
        val trimmedMagic = code.substring(interval.from, interval.to).trim()
        languageInfo = NotebookExtraLanguage.entries.firstOrNull { info ->
            info.magics.any { magic ->
                AbstractMagicsProcessor.MAGICS_SIGN + magic == trimmedMagic
            }
        }
    }

    // We should always have at least one code range
    if (!codeInserted || codeStart != code.length) {
        yieldCode(codeStart, code.length)
    }
}

private data class IntervalWithLanguage(
    val interval: TextRange,

    // Null means default language (Kotlin)
    val languageInfo: NotebookExtraLanguage?,
)
