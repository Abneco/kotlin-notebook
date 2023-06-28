// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.stats

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import org.jetbrains.kotlinx.jupyter.plugin.settings.*
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterCellType
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterNotebook

private class KotlinNotebookFeatureUsagesCollector : FeatureUsagesCollector() {
    override fun getGroup(): EventLogGroup {
        return KotlinNotebookFusLogger.GROUP
    }
}

object KotlinNotebookFusLogger {
    val GROUP = EventLogGroup("kotlin.notebook", 1)

    private object Fields {
        val CELLS_COUNT = EventFields.Int("cells_count")
        val CODE_CELLS_COUNT = EventFields.Int("cells_code_count")
        val MARKDOWN_CELLS_COUNT = EventFields.Int("cells_markdown_count")

        val NOTEBOOK_LANGUAGE = EventFields.Language

        val INCLUDED_PROJECT_MODULES = EventFields.Int("project_sources_v2")
        val INCLUDED_PROJECT_LIBRARIES = EventFields.Int("project_libraries_v2")
        val PROJECT_SOURCE_DEPENDENCIES_INCLUDED = EventFields.Boolean("project_sources_v1_included")
        val PROJECT_LIBRARY_DEPENDENCIES_INCLUDED = EventFields.Boolean("project_libraries_v1_included")
    }

    private val NOTEBOOK_OPEN_EVENT = GROUP.registerVarargEvent(
      "notebook.open",
      Fields.CELLS_COUNT,
      Fields.CODE_CELLS_COUNT,
      Fields.MARKDOWN_CELLS_COUNT,
      Fields.NOTEBOOK_LANGUAGE,
      Fields.INCLUDED_PROJECT_MODULES,
      Fields.INCLUDED_PROJECT_LIBRARIES,
      Fields.PROJECT_SOURCE_DEPENDENCIES_INCLUDED,
      Fields.PROJECT_LIBRARY_DEPENDENCIES_INCLUDED,
    )

    fun registerOpenNotebook(file: JupyterNotebook) {
        val cellsCount = file.cells.size

        var markdownCellsCount = 0
        var codeCellsCount = 0
        file.cells.forEach { cell ->
            when(cell.cellType) {
                JupyterCellType.RAW, JupyterCellType.UNDEFINED -> {}
                JupyterCellType.MARKDOWN -> {
                    ++markdownCellsCount
                }
                JupyterCellType.CODE_OR_MAGIC, JupyterCellType.CODE, JupyterCellType.MAGIC -> {
                    ++codeCellsCount
                }
            }
        }
        NOTEBOOK_OPEN_EVENT.log(
          Fields.CELLS_COUNT.with(cellsCount),
          Fields.CODE_CELLS_COUNT.with(codeCellsCount),
          Fields.MARKDOWN_CELLS_COUNT.with(markdownCellsCount),
          Fields.NOTEBOOK_LANGUAGE.with(file.language),
          Fields.INCLUDED_PROJECT_MODULES.with(file.projectDependencies.count()),
          Fields.INCLUDED_PROJECT_LIBRARIES.with(file.projectLibraries.count()),
          Fields.PROJECT_SOURCE_DEPENDENCIES_INCLUDED.with(file.isBuildProject),
          Fields.PROJECT_LIBRARY_DEPENDENCIES_INCLUDED.with(file.isAddProjectLibrariesToClasspath),
        )
    }

    private fun KotlinNotebookDependencies.count() = when(this) {
        is KotlinNotebookDependencies.All -> -2
        is KotlinNotebookDependencies.Selection -> this.values.size
    }
}
