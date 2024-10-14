package com.intellij.kotlin.jupyter.core.jupyter.outputs.error

private val cellRegex = ("at Cell In\\[(?<executionCount>\\d+)\\], (?<highlight>line (?<lineNumber>\\d+))").toRegex()

/**
 * Find any cell references in a single line and return the information
 * that makes it possible to highlight and turn it into a link.
 *
 * @param line a single line of text from a bigger string, usually a stacktrace.
 * @param entireLength the total length of the entire string. It is always `entireLength >= line.length`.
 */
fun linkifyStackLine(line: String, entireLength: Int): CellHighlightInfo? {
        val match: MatchResult = cellRegex.find(line) ?: return null
        val highlightText = match.groups["highlight"] ?: return null
        val executionCountRaw = match.groups["executionCount"] ?: return null
        val cellLineRaw = match.groups["lineNumber"] ?: return null

        val runCount = executionCountRaw.value.toIntOrNull() ?: return null
        val cellLine = cellLineRaw.value.toIntOrNull() ?: return null

        val range = highlightText.range
        val startLine = entireLength - line.length

        return CellHighlightInfo((startLine + range.first)..(startLine + range.last + 1), runCount, cellLine)
}

data class CellHighlightInfo(val highlightRange: IntRange, val executionCount: Int, val cellLine: Int)
