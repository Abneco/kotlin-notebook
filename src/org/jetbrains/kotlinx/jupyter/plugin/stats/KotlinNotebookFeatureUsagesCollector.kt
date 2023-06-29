// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.stats

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import org.jetbrains.kotlinx.jupyter.plugin.settings.*
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterCellType
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterNotebook

class KotlinNotebookFeatureUsagesCollector : FeatureUsagesCollector() {
    override fun getGroup(): EventLogGroup {
        return GROUP
    }

    @Suppress("CompanionObjectInExtension")
    companion object {
        @JvmStatic private val GROUP = EventLogGroup("kotlin.notebook", 1)

        @JvmStatic private val CELLS_COUNT = EventFields.Int("cells_count")
        @JvmStatic private val CODE_CELLS_COUNT = EventFields.Int("cells_code_count")
        @JvmStatic private val MARKDOWN_CELLS_COUNT = EventFields.Int("cells_markdown_count")

        @JvmStatic private val NOTEBOOK_LANGUAGE = EventFields.Language

        @JvmStatic private val INCLUDED_PROJECT_MODULES = EventFields.Int("project_sources_v2")

        @JvmStatic private val INCLUDED_PROJECT_LIBRARIES = EventFields.Int("project_libraries_v2")
        @JvmStatic private val PROJECT_SOURCE_DEPENDENCIES_INCLUDED = EventFields.Boolean("project_sources_v1_included")
        @JvmStatic private val PROJECT_LIBRARY_DEPENDENCIES_INCLUDED = EventFields.Boolean("project_libraries_v1_included")

        @JvmStatic private val NOTEBOOK_OPEN_EVENT = GROUP.registerVarargEvent(
            "notebook.open",
            CELLS_COUNT,
            CODE_CELLS_COUNT,
            MARKDOWN_CELLS_COUNT,
            NOTEBOOK_LANGUAGE,
            INCLUDED_PROJECT_MODULES,
            INCLUDED_PROJECT_LIBRARIES,
            PROJECT_SOURCE_DEPENDENCIES_INCLUDED,
            PROJECT_LIBRARY_DEPENDENCIES_INCLUDED,
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
                CELLS_COUNT.with(cellsCount),
                CODE_CELLS_COUNT.with(codeCellsCount),
                MARKDOWN_CELLS_COUNT.with(markdownCellsCount),
                NOTEBOOK_LANGUAGE.with(file.language),
                INCLUDED_PROJECT_MODULES.with(file.projectDependencies.count()),
                INCLUDED_PROJECT_LIBRARIES.with(file.projectLibraries.count()),
                PROJECT_SOURCE_DEPENDENCIES_INCLUDED.with(file.isBuildProject),
                PROJECT_LIBRARY_DEPENDENCIES_INCLUDED.with(file.isAddProjectLibrariesToClasspath),
            )
        }

        private fun KotlinNotebookDependencies.count() = when(this) {
            is KotlinNotebookDependencies.All -> -2
            is KotlinNotebookDependencies.Selection -> this.values.size
        }
    }
}
