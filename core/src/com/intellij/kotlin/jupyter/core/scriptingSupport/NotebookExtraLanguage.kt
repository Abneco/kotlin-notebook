// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.scriptingSupport

enum class NotebookExtraLanguage(
    val magics: List<String>,
    val id: String,
    val extension: String,
) {
    // These language magics are defined in https://github.com/yidafu/kotlin-jupyter-js
    // They can be turned on by `%use jupyter-js` magic
    JAVASCRIPT(listOf("js", "javascript"), "JavaScript", "js"),
    TYPESCRIPT(listOf("ts", "typescript"), "TypeScript", "ts"),
    TYPESCRIPT_JSX(listOf("tsx", "jsx"), "TypeScript JSX", "tsx"),
}
