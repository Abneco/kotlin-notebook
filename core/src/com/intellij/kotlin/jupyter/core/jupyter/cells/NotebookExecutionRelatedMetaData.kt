// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.cells

import com.fasterxml.jackson.annotation.JsonInclude
import com.intellij.jupyter.core.jupyter.nbformat.JupyterCell
import com.intellij.kotlin.jupyter.core.jupyter.cells.NotebookExecutionRelatedMetaData.Companion.DATA_KEY

@JsonInclude(JsonInclude.Include.NON_NULL)
data class NotebookExecutionRelatedMetaData(
    val compiledClasses: List<String> = emptyList()
) : StorableCellMetadata {
    companion object {
        internal val DATA_KEY: StorableCellMetadataKey<NotebookExecutionRelatedMetaData> = StorableCellMetadataKey(
            "executionRelatedData",
            NotebookExecutionRelatedMetaData::class.java
        )

        internal fun JupyterCell.storeExecutionRelatedMetaData(classes: Collection<String>) {
            val presentClasses = executionMetadata?.compiledClasses ?: emptyList()
            executionMetadata = NotebookExecutionRelatedMetaData(compiledClasses = presentClasses + classes)
        }
    }
}

val JupyterCell.hasExecutionData: Boolean
    get() = this.executionMetadata != null

var JupyterCell.executionMetadata: NotebookExecutionRelatedMetaData? by cellMetadataAccessor(DATA_KEY)


