// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.stats

import com.intellij.internal.statistic.LibraryNameValidationRule
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.libraryUsage.LibraryUsageDescriptors
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.compiler.util.EvaluatedSnippetMetadata
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.plugin.outputs.plots.PlotDataKeyExtractor
import org.jetbrains.kotlinx.jupyter.plugin.outputs.tables.KotlinDataframeParsing
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookDependencies
import org.jetbrains.kotlinx.jupyter.plugin.settings.isAddProjectLibrariesToClasspath
import org.jetbrains.kotlinx.jupyter.plugin.settings.isBuildProject
import org.jetbrains.kotlinx.jupyter.plugin.settings.projectDependencies
import org.jetbrains.kotlinx.jupyter.plugin.settings.projectLibraries
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.JupyterExecutionStatus
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.executionCount
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.message.JupyterMessage
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.status
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterCellType
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterDisplayDataOutput
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterErrorOutput
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterNotebook
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterOutput
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterStreamOutput

class KotlinNotebookFeatureUsagesCollector : FeatureUsagesCollector() {
    override fun getGroup(): EventLogGroup {
        return GROUP
    }

    @Suppress("CompanionObjectInExtension")
    companion object {
        @JvmStatic private val GROUP = EventLogGroup("kotlin.notebook", 4)

        @JvmStatic private val CELLS_COUNT = EventFields.RoundedInt("cells_count")
        @JvmStatic private val CODE_CELLS_COUNT = EventFields.RoundedInt("cells_code_count")
        @JvmStatic private val MARKDOWN_CELLS_COUNT = EventFields.RoundedInt("cells_markdown_count")

        @JvmStatic private val NOTEBOOK_LANGUAGE = EventFields.Language

        @JvmStatic private val INCLUDED_PROJECT_MODULES_COUNT = EventFields.RoundedInt("project_sources_v2_count")
        @JvmStatic private val INCLUDED_PROJECT_LIBRARIES_COUNT = EventFields.RoundedInt("project_libraries_v2_count")
        @JvmStatic private val ARE_PROJECT_SOURCE_DEPENDENCIES_INCLUDED = EventFields.Boolean("project_sources_v1_included")
        @JvmStatic private val ARE_PROJECT_LIBRARY_DEPENDENCIES_INCLUDED = EventFields.Boolean("project_libraries_v1_included")

        @JvmStatic private val NOTEBOOK_OPEN_EVENT = GROUP.registerVarargEvent(
            "notebook.opened",
            CELLS_COUNT,
            CODE_CELLS_COUNT,
            MARKDOWN_CELLS_COUNT,
            NOTEBOOK_LANGUAGE,
            INCLUDED_PROJECT_MODULES_COUNT,
            INCLUDED_PROJECT_LIBRARIES_COUNT,
            ARE_PROJECT_SOURCE_DEPENDENCIES_INCLUDED,
            ARE_PROJECT_LIBRARY_DEPENDENCIES_INCLUDED,
        )

        fun registerOpenNotebook(project: Project, file: JupyterNotebook) {
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
                project,
                CELLS_COUNT.with(cellsCount),
                CODE_CELLS_COUNT.with(codeCellsCount),
                MARKDOWN_CELLS_COUNT.with(markdownCellsCount),
                NOTEBOOK_LANGUAGE.with(file.language),
                INCLUDED_PROJECT_MODULES_COUNT.with(file.projectDependencies.count()),
                INCLUDED_PROJECT_LIBRARIES_COUNT.with(file.projectLibraries.count()),
                ARE_PROJECT_SOURCE_DEPENDENCIES_INCLUDED.with(file.isBuildProject),
                ARE_PROJECT_LIBRARY_DEPENDENCIES_INCLUDED.with(file.isAddProjectLibrariesToClasspath),
            )
        }

        private enum class ExecutionStatus {
            OK, COMPILATION_ERROR, RUNTIME_ERROR, ABORTED
        }

        @JvmStatic private val EXECUTION_STATUS = EventFields.Enum<ExecutionStatus>("cell_execution_status")
        @JvmStatic private val CLASSPATH_ENTRIES_COUNT = EventFields.RoundedInt("classpath_entries_count")
        @JvmStatic private val EXECUTION_TIME = EventFields.DurationMs
        @JvmStatic private val EXECUTION_COUNT = EventFields.RoundedInt("executed_cells_count")

        @JvmStatic private val EXECUTION_RESULT_EVENT = GROUP.registerVarargEvent(
            "cell.result.received",
            EXECUTION_STATUS,
            CLASSPATH_ENTRIES_COUNT,
            EXECUTION_TIME,
            EXECUTION_COUNT,
        )

        @JvmStatic private val LIBRARY_USED_EVENT = GROUP.registerEvent(
            "library.used",
            EventFields.StringValidatedByCustomRule("library_name", LibraryNameValidationRule::class.java)
        )

        fun registerCellExecuted(
            project: Project,
            message: JupyterMessage,
            executionDurationMs: Long,
            metadata: EvaluatedSnippetMetadata
        ) {
            val status = when(message.status) {
                JupyterExecutionStatus.OK -> ExecutionStatus.OK
                JupyterExecutionStatus.ERROR -> {
                    val errorName = message.messageContent["ename"].asText("")
                    val compileErrorClassName = ReplCompilerException::class.qualifiedName.orEmpty()
                    if (compileErrorClassName in errorName) {
                        ExecutionStatus.COMPILATION_ERROR
                    } else {
                        ExecutionStatus.RUNTIME_ERROR
                    }
                }
                JupyterExecutionStatus.ABORTED -> {
                    ExecutionStatus.ABORTED
                }
            }
            val classpathEntriesCount = metadata.newClasspath.size

            EXECUTION_RESULT_EVENT.log(
                project,
                listOfNotNull(
                    EXECUTION_STATUS.with(status),
                    CLASSPATH_ENTRIES_COUNT.with(classpathEntriesCount),
                    EXECUTION_TIME.with(executionDurationMs),
                    message.executionCount?.let { EXECUTION_COUNT.with(it) }
                )
            )

            metadata.newImports.mapNotNullTo(mutableSetOf()) { import ->
                LibraryUsageDescriptors.findSuitableLibrary(import)
            }.forEach { libraryName ->
                LIBRARY_USED_EVENT.log(project, libraryName)
            }
        }


        private enum class OutputType {
            ERROR,
            STREAM_ERROR,
            STREAM_TEXT,
            OTHER,

            PLAIN_TEXT,
            HTML,
            MARKDOWN,
            JSON,
            RASTER_IMAGE,
            VECTOR_IMAGE,
            SWING_LETS_PLOT,
            SWING_DATAFRAME,
        }

        private val mimeToOutputType = mapOf(
            "text/plain" to OutputType.PLAIN_TEXT,
            "text/html" to OutputType.HTML,
            "text/markdown" to OutputType.MARKDOWN,
            "application/json" to OutputType.JSON,
            "image/png" to OutputType.RASTER_IMAGE,
            "image/jpeg" to OutputType.RASTER_IMAGE,
            "image/bmp" to OutputType.RASTER_IMAGE,
            "image/svg+xml" to OutputType.VECTOR_IMAGE,
            PlotDataKeyExtractor.PLOT_KEY to OutputType.SWING_LETS_PLOT,
            KotlinDataframeParsing.jsonPayloadField to OutputType.SWING_DATAFRAME,
        )

        @JvmStatic private val OUTPUT_UPDATED_EVENT = GROUP.registerEvent(
            "output.updated",
            EventFields.StringList("output_types", OutputType.values().map { it.toString() })
        )

        fun registerOutputUpdated(project: Project, output: JupyterOutput) {
            val outputTypes: List<OutputType> = when(output) {
                is JupyterErrorOutput -> { listOf(OutputType.ERROR) }
                is JupyterStreamOutput -> {
                    val type = if (output.name == "stdout") {
                        OutputType.STREAM_TEXT
                    } else {
                        OutputType.STREAM_ERROR
                    }
                    listOf(type)
                }
                is JupyterDisplayDataOutput -> {
                    buildList {
                        output.data.properties().forEach { (key, _) ->
                            add(mimeToOutputType[key] ?: OutputType.OTHER)
                        }
                    }
                }
                else -> emptyList()
            }

            OUTPUT_UPDATED_EVENT.log(project, outputTypes.map { it.toString() })
        }

        @JvmStatic private val KERNEL_RESTARTED_EVENT = GROUP.registerEvent(
            "kernel.restarted",
            CELLS_COUNT,
            CLASSPATH_ENTRIES_COUNT,
        )

        fun registerKernelRestart(
            project: Project,
            cellCountBeforeRestart: Int,
            classpathSizeBeforeRestart: Int,
        ) {
            KERNEL_RESTARTED_EVENT.log(project, cellCountBeforeRestart, classpathSizeBeforeRestart)
        }

        @JvmStatic private val ALL_CELLS_RUN_EVENT = GROUP.registerEvent(
            "notebook.cells.all.run",
            CELLS_COUNT,
        )

        fun registerRunAllCells(project: Project, cellCountToRun: Int) {
            ALL_CELLS_RUN_EVENT.log(project, cellCountToRun)
        }

        private fun KotlinNotebookDependencies.count() = when(this) {
            is KotlinNotebookDependencies.All -> -2
            is KotlinNotebookDependencies.Selection -> this.values.size
        }
    }
}
