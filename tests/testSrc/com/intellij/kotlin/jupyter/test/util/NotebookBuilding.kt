// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.util

import com.intellij.kotlin.jupyter.test.util.data.parseFromRawInput
import com.intellij.openapi.util.io.FileUtilRt
import org.intellij.lang.annotations.Language
import org.jetbrains.jupyter.builder.NotebookBuilder
import java.nio.file.Path

fun NotebookBuilder.kotlinCell(@Language("kotlin") kotlin: String) {
    codeCell(kotlin)
}

/**
 * Build's a notebook from a given [file].
 * It's expected that a file follows a template test data format.
 */
fun NotebookBuilder.fromTemplateFile(file: Path) {
    val templateContent = FileUtilRt.loadFile(file.toFile(), true)
    parseFromRawInput(templateContent)
}
