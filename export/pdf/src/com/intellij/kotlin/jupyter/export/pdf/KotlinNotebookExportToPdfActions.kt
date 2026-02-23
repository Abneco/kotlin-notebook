// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.export.pdf

import com.intellij.jupyter.convert.ConversionMode
import com.intellij.jupyter.convert.NotebookExportToPdfActionBase
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.vfs.VirtualFile

internal class KotlinNotebookFullExportToPdfAction : KotlinNotebookExportToPdfActionBase(ConversionMode.EntireNotebook)
internal class KotlinNotebookOutputsExportToPdfAction : KotlinNotebookExportToPdfActionBase(ConversionMode.OnlyOutputs)
internal class KotlinNotebookOutputsAndMarkdownExportToPdfAction : KotlinNotebookExportToPdfActionBase(ConversionMode.MarkdownAndOutputs)

internal sealed class KotlinNotebookExportToPdfActionBase(
  conversionMode: ConversionMode
) : NotebookExportToPdfActionBase(conversionMode) {

  override fun isApplicableNotebook(file: VirtualFile): Boolean = file.isKotlinNotebook
}
