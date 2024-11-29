// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.service

import com.intellij.kotlin.jupyter.core.editor.highlighting.service.pass.InjectedFilesDataTracker
import com.intellij.openapi.editor.markup.RangeHighlighter
import org.jetbrains.kotlin.psi.KtFile

/**
 * Data class used to represent pass setup upon initialization.
 * Info is constructed using [com.intellij.kotlin.jupyter.core.editor.hack.HighlightingEvent].
 */
internal data class NotebookPassConfiguration(
    val focusCell: Int,
    val filesToHL: Map<KtFile, InjectedFilesDataTracker.InjectedFileData>,
    val completedFiles: MutableSet<Int>,
    val errorHighlighters: MutableSet<RangeHighlighter>
)
