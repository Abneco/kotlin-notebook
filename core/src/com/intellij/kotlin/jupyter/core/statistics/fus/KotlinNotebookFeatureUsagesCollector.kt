// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.statistics.fus

import com.intellij.internal.statistic.LibraryNameValidationRule
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.libraryUsage.LibraryUsageDescriptors
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.JupyterExecutionStatus
import com.intellij.jupyter.core.jupyter.connections.execution.executionCount
import com.intellij.jupyter.core.jupyter.connections.execution.message.JupyterMessage
import com.intellij.jupyter.core.jupyter.connections.execution.status
import com.intellij.jupyter.core.jupyter.nbformat.outputs.JupyterOutput
import com.intellij.jupyter.core.jupyter.nbformat.MimeType
import com.intellij.jupyter.core.jupyter.nbformat.outputs.JupyterDisplayDataOutput
import com.intellij.jupyter.core.jupyter.nbformat.outputs.JupyterErrorOutput
import com.intellij.jupyter.core.jupyter.nbformat.outputs.JupyterStreamOutput
import com.intellij.kotlin.jupyter.core.jupyter.actions.NotebookMode
import com.intellij.kotlin.jupyter.core.jupyter.actions.mode
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookDependencies
import com.intellij.kotlin.jupyter.core.settings.isAddProjectLibrariesToClasspath
import com.intellij.kotlin.jupyter.core.settings.isBuildProject
import com.intellij.kotlin.jupyter.core.settings.notebookDependencies
import com.intellij.notebooks.jupyter.core.jupyter.CellType
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.exceptions.ReplCompilerException
import org.jetbrains.kotlinx.jupyter.repl.EvaluatedSnippetMetadata

class KotlinNotebookFeatureUsagesCollector : FeatureUsagesCollector() {
    override fun getGroup(): EventLogGroup {
        return GROUP
    }

    @Suppress("CompanionObjectInExtension")
    companion object {
        @JvmStatic
        private val GROUP = EventLogGroup("kotlin.notebook", 7)

        @JvmStatic
        private val CELLS_COUNT = EventFields.RoundedInt("cells_count")

        @JvmStatic
        private val CODE_CELLS_COUNT = EventFields.RoundedInt("cells_code_count")

        @JvmStatic
        private val MARKDOWN_CELLS_COUNT = EventFields.RoundedInt("cells_markdown_count")

        @JvmStatic
        private val NOTEBOOK_LANGUAGE = EventFields.Language

        @JvmStatic
        private val NOTEBOOK_MODE = EventFields.Enum<NotebookMode>("notebook_mode") { it.id }

        @JvmStatic
        private val INCLUDED_PROJECT_MODULES_COUNT = EventFields.RoundedInt("project_sources_v2_count")

        @JvmStatic
        private val INCLUDED_PROJECT_LIBRARIES_COUNT = EventFields.RoundedInt("project_libraries_v2_count")

        @JvmStatic
        private val ARE_PROJECT_SOURCE_DEPENDENCIES_INCLUDED = EventFields.Boolean("project_sources_v1_included")

        @JvmStatic
        private val ARE_PROJECT_LIBRARY_DEPENDENCIES_INCLUDED = EventFields.Boolean("project_libraries_v1_included")

        @JvmStatic
        private val NOTEBOOK_OPEN_EVENT = GROUP.registerVarargEvent(
            "notebook.opened",
            CELLS_COUNT,
            CODE_CELLS_COUNT,
            MARKDOWN_CELLS_COUNT,
            NOTEBOOK_LANGUAGE,
            INCLUDED_PROJECT_MODULES_COUNT,
            INCLUDED_PROJECT_LIBRARIES_COUNT,
            ARE_PROJECT_SOURCE_DEPENDENCIES_INCLUDED,
            ARE_PROJECT_LIBRARY_DEPENDENCIES_INCLUDED,
            NOTEBOOK_MODE
        )

        fun registerOpenNotebook(project: Project, file: BackedNotebookVirtualFile) {
            val notebook = file.notebook
            val cellsCount = notebook.cellsCount()

            var markdownCellsCount = 0
            var codeCellsCount = 0
            notebook.computeCells().forEach { cell ->
                when (cell.cellTypeProvider.getCellType()) {
                    CellType.RAW -> {
                        //nothing
                    }
                    CellType.MARKDOWN -> {
                        ++markdownCellsCount
                    }
                    CellType.CODE -> {
                        ++codeCellsCount
                    }
                }
            }

            NOTEBOOK_OPEN_EVENT.log(
                project,
                CELLS_COUNT.with(cellsCount),
                CODE_CELLS_COUNT.with(codeCellsCount),
                MARKDOWN_CELLS_COUNT.with(markdownCellsCount),
                NOTEBOOK_LANGUAGE.with(notebook.language),
                INCLUDED_PROJECT_MODULES_COUNT.with(
                    if (notebook.notebookDependencies is KotlinNotebookDependencies.SingleModule) 1 else 0
                ),
                INCLUDED_PROJECT_LIBRARIES_COUNT.with(
                    if (notebook.notebookDependencies is KotlinNotebookDependencies.AllLibraries) ALL_LIBRARIES_COUNT else 0
                ),
                ARE_PROJECT_SOURCE_DEPENDENCIES_INCLUDED.with(notebook.isBuildProject),
                ARE_PROJECT_LIBRARY_DEPENDENCIES_INCLUDED.with(notebook.isAddProjectLibrariesToClasspath),
                NOTEBOOK_MODE.with(file.mode)
            )
        }

        private enum class ExecutionStatus {
            OK, COMPILATION_ERROR, RUNTIME_ERROR, ABORTED
        }

        @JvmStatic
        private val EXECUTION_STATUS = EventFields.Enum<ExecutionStatus>("cell_execution_status")

        @JvmStatic
        private val CLASSPATH_ENTRIES_COUNT = EventFields.RoundedInt("classpath_entries_count")

        @JvmStatic
        private val EXECUTION_TIME = EventFields.DurationMs

        @JvmStatic
        private val EXECUTION_COUNT = EventFields.RoundedInt("executed_cells_count")

        @JvmStatic
        private val EXECUTION_RESULT_EVENT = GROUP.registerVarargEvent(
            "cell.result.received",
            EXECUTION_STATUS,
            CLASSPATH_ENTRIES_COUNT,
            EXECUTION_TIME,
            EXECUTION_COUNT,
        )

        @JvmStatic
        private val LIBRARY_NAME = EventFields.StringValidatedByCustomRule("library_name", LibraryNameValidationRule::class.java)

        @JvmStatic
        private val LIBRARY_USED_EVENT = GROUP.registerVarargEvent(
            "library.used",
            LIBRARY_NAME,
            EXECUTION_COUNT,
        )

        fun registerCellExecuted(
            project: Project,
            message: JupyterMessage,
            executionDurationMs: Long,
            metadata: EvaluatedSnippetMetadata
        ) {
            val status = when (message.status) {
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
            val executionCountPair = message.executionCount?.let { EXECUTION_COUNT.with(it) }

            EXECUTION_RESULT_EVENT.log(
                project,
                listOfNotNull(
                    EXECUTION_STATUS.with(status),
                    CLASSPATH_ENTRIES_COUNT.with(classpathEntriesCount),
                    EXECUTION_TIME.with(executionDurationMs),
                    executionCountPair
                )
            )

            metadata.newImports.mapNotNullTo(mutableSetOf()) { import ->
                LibraryUsageDescriptors.findSuitableLibrary(import)
            }.forEach { libraryName ->
                LIBRARY_USED_EVENT.log(
                    project,
                    listOfNotNull(
                        LIBRARY_NAME.with(libraryName),
                        executionCountPair,
                    )
                )
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
            MimeType.TEXT_PLAIN.mimeType to OutputType.PLAIN_TEXT,
            MimeType.TEXT_HTML.mimeType to OutputType.HTML,
            MimeType.TEXT_MARKDOWN.mimeType to OutputType.MARKDOWN,
            MimeType.APPLICATION_JSON.mimeType to OutputType.JSON,
            MimeType.IMAGE_PNG.mimeType to OutputType.RASTER_IMAGE,
            MimeType.IMAGE_JPG.mimeType to OutputType.RASTER_IMAGE,
            MimeType.IMAGE_BMP.mimeType to OutputType.RASTER_IMAGE,
            MimeType.IMAGE_SVG.mimeType to OutputType.VECTOR_IMAGE,
            MimeType.LETS_PLOT.mimeType to OutputType.SWING_LETS_PLOT,
            MimeType.KOTLIN_DATAFRAME.mimeType to OutputType.SWING_DATAFRAME,
        )

        @JvmStatic
        private val OUTPUT_UPDATED_EVENT = GROUP.registerEvent(
            "output.updated",
            EventFields.StringList("output_types", OutputType.entries.map { it.toString() })
        )

        fun registerOutputUpdated(project: Project, output: JupyterOutput) {
            val outputTypes: List<OutputType> = when (output) {
                is JupyterErrorOutput -> {
                    listOf(OutputType.ERROR)
                }
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
                        output.data.items.forEach { key ->
                            add(mimeToOutputType[key] ?: OutputType.OTHER)
                        }
                    }
                }
                else -> emptyList()
            }

            OUTPUT_UPDATED_EVENT.log(project, outputTypes.map { it.toString() })
        }

        @JvmStatic
        private val KERNEL_RESTARTED_EVENT = GROUP.registerEvent(
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

        @JvmStatic
        private val ALL_CELLS_RUN_EVENT = GROUP.registerEvent(
            "notebook.cells.all.run",
            CELLS_COUNT,
        )

        fun registerRunAllCells(project: Project, cellCountToRun: Int) {
            ALL_CELLS_RUN_EVENT.log(project, cellCountToRun)
        }

        @JvmStatic
        private val KOTLIN_NOTEBOOK_WELCOME_SCREEN_TAB_OPENED_EVENT = GROUP.registerEvent(
            "welcome.screen.tab.opened"
        )

        fun registerWelcomeScreenTabOpened() {
            KOTLIN_NOTEBOOK_WELCOME_SCREEN_TAB_OPENED_EVENT.log(null)
        }

        @JvmStatic
        private val IDE_ENTRY_TYPE = EventFields.Enum<WelcomeScreenIdeEntryType>("ide_entry_type")

        @JvmStatic
        private val ENTERED_IDE_FROM_KOTLIN_NOTEBOOK_WELCOME_SCREEN_EVENT = GROUP.registerEvent(
            "welcome.screen.ide.entered",
            IDE_ENTRY_TYPE,
        )

        fun registerIdeEntryFromKotlinNotebookWelcomeScreen(ideEntryType: WelcomeScreenIdeEntryType) {
            ENTERED_IDE_FROM_KOTLIN_NOTEBOOK_WELCOME_SCREEN_EVENT.log(
                null,
                ideEntryType
            )
        }

        // We need some negative special value for "all" dependencies to avoid requesting them
        // Note that all the special values should be negative powers of 2
        private const val ALL_LIBRARIES_COUNT = -2
    }
}
