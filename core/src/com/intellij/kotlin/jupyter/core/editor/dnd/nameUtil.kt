// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

// Copied from https://github.com/Kotlin/kotlin-notebook-integrations/blob/master/integrations/widgets/widgets-generator/src/main/kotlin/org/jetbrains/kotlinx/jupyter/widget/generator/StringUtil.kt

private val commonAbbreviations: Set<String> =
    setOf(
        "html",
        "http",
        "xml",
        "csv",
        "dom",
    )

/**
 * Converts a string to camelCase, taking into account common abbreviations and delimiters.
 */
fun String.toCamelCase(): String {
    val parts = splitIntoParts()
    if (parts.isEmpty()) return this

    return buildString {
        append(parts[0].lowercase())
        for (i in 1 until parts.size) {
            append(parts[i].toPascalCasePart())
        }
    }
}

/**
 * Splits a string into parts based on common delimiters (underscore, hyphen, space)
 * or by detecting transitions between lowercase and uppercase letters (PascalCase/camelCase).
 * Abbreviations are treated as separate parts.
 */
private fun String.splitIntoParts(): List<String> {
    if (isEmpty()) return emptyList()

    val parts = mutableListOf<String>()
    val currentPart = StringBuilder()

    fun startNewPart() {
        parts.add(currentPart.toString())
        currentPart.clear()
    }

    var i = 0
    while (i < length) {
        val c = this[i]
        if (c == '_' || c == '-' || c == ' ') {
            if (currentPart.isNotEmpty()) {
                startNewPart()
            }
            i++
            continue
        }

        // Check for common abbreviations
        val abbreviation = commonAbbreviations.find {
            regionMatches(i, it, 0, it.length, ignoreCase = true)
        }
        if (abbreviation != null) {
            if (currentPart.isNotEmpty()) {
                startNewPart()
            }
            parts.add(abbreviation)
            i += abbreviation.length
            continue
        }

        // Detect PascalCase/camelCase and digit/non-digit transitions
        if (currentPart.isNotEmpty()) {
            val last = currentPart.last()
            if (
                c.isUpperCase() && (!last.isUpperCase() || (i + 1 < length && this[i + 1].isLowerCase())) ||
                c.isDigit() != last.isDigit()
            ) {
                startNewPart()
            }
        }

        currentPart.append(c)
        i++
    }
    if (currentPart.isNotEmpty()) {
        parts.add(currentPart.toString())
    }
    return parts
}

private fun String.toPascalCasePart(): String = lowercase().replaceFirstChar { it.uppercase() }

