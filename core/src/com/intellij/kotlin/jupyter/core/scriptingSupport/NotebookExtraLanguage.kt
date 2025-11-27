// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

enum class NotebookExtraLanguage(
    val magics: List<String>,
    val id: String,
    val extension: String,
) {
    JAVASCRIPT(listOf("js"), "JavaScript", "js"),
    TYPESCRIPT(listOf("ts"), "TypeScript", "ts"),
}
