// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

// Copied from Kotlin Dataframe library sources

private const val DELIMITERS = "[-_\\s]"
private val DELIMITERS_REGEX: Regex = DELIMITERS.toRegex()

fun String.toCamelCase(delimiters: Regex = DELIMITERS_REGEX): String =
    split(delimiters).joinToCamelCaseString()

private fun List<String>.joinToCamelCaseString(): String {
    return joinToString(separator = "") { s ->
        s.replaceFirstChar { c -> c.uppercaseChar() }
    }.replaceFirstChar { c -> c.lowercaseChar() }
}
