// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.util.data

import com.intellij.jupyter.core.core.impl.actions.NotebookCellsContents.Companion.CELL_HEADER
import org.jetbrains.jupyter.builder.NotebookBuilder

/**
 * This regex selects '#%%', not allowing for any whitespace chars to be placed
 * before the separator itself.
 * The separator line remains as part of the resulting block.
 *
 * @see CELL_HEADER
 */
internal val CELL_SEPARATOR_REGEX = Regex("(?m)(?=^#%%)")

/**
 * This extension is used to identify file templates which
 * will be used to build notebooks later on.
 * It's expected that cells are separated by [CELL_HEADER], which may contain either code or md content.
 *
 * Format example:
 * ```
 * #%%
 * val a = 12
 * class A {}
 * #%% md
 * Some *markdown* here
 * #%%
 * val x = a
 * ```
 *
 * @see [NotebookBuilder.parseFromRawInput]
 */
internal const val TEMPLATE_DATA_EXTENSION = "ktnb"