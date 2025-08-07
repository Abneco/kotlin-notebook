// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.hack.pass

import com.intellij.openapi.util.TextRange

data class NotebookCellFocusInformation(
    val focusCellIndex: Int,
    val range: TextRange,
)