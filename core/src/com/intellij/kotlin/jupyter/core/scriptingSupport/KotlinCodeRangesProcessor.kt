// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlinx.jupyter.common.looksLikeReplCommand
import org.jetbrains.kotlinx.jupyter.compiler.util.CodeInterval
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.NoopMagicsHandler
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterSource

object KotlinCodeRangesProcessor {
    private val magicsProcessor = MagicsProcessor(
        handler = NoopMagicsHandler,
        parseOutCellMarker = true
    )

    private fun getCellCode(cell: PsiElement): String {
        val sourceElement = PsiTreeUtil.getChildOfType(cell, JupyterSource::class.java)
        val source = sourceElement?.text.orEmpty()
        return source.trimStart()
    }

    fun codeRanges(cell: JupyterPsiCell): CodeRangesResult {
        val code = getCellCode(cell)
        if (looksLikeReplCommand(code)) return CodeRangesResult(
            CellRanges(
                emptyList(),
                listOf(
                    TextRange(
                        0,
                        cell.textLength
                    )
                )
            ), true)

        val text = cell.text
        val magicIntervals = magicsProcessor.magicsIntervals(text)

        fun Sequence<CodeInterval>.toRanges() = mapTo(mutableListOf()) {
            TextRange(it.from, it.to)
        }

        val codeRanges = magicsProcessor.codeIntervals(text, magicIntervals).toRanges()
        val magicRanges = magicIntervals.toRanges()

        insertEmptyCodeRange(codeRanges, magicRanges)

        return CodeRangesResult(CellRanges(codeRanges, magicRanges), false)
    }

    private fun insertEmptyCodeRange(
        codeRanges: MutableList<TextRange>,
        magicRanges: List<TextRange>
    ) {
        val lastCodeRange = codeRanges.lastOrNull()
        val lastMagicRange = magicRanges.lastOrNull()

        var startOffset = -1

        if (lastCodeRange == null) {
            startOffset = lastMagicRange?.endOffset ?: 0
        } else if (lastMagicRange != null && lastCodeRange.startOffset < lastMagicRange.startOffset) {
            startOffset = lastMagicRange.endOffset
        }

        if (startOffset != -1) {
            codeRanges.add(TextRange(startOffset, startOffset))
        }
    }

    data class CodeRangesResult(
        val ranges: CellRanges,
        val isCommand: Boolean,
    )

    data class CellRanges(val codeRanges: List<TextRange>, val magicRanges: List<TextRange>)
}
