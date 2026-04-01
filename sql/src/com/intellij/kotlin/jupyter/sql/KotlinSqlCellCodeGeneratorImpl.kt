// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.sql

import com.intellij.dataspell.jupyter.sql.common.Code
import com.intellij.dataspell.jupyter.sql.common.SqlCellsCodeGenerator
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.project.Project
import java.util.Base64
import kotlin.io.path.name

/**
 * Generates kotlin code required for SQL cells in kotlin notebooks to work.
 *
 * There's a high likelihood that the code produced in this class will be transmitted to the kernel for execution.
 */
class KotlinSqlCellCodeGeneratorImpl : SqlCellsCodeGenerator {

    override fun isApplicable(notebook: JupyterNotebook): Boolean = notebook.isKotlinNotebook

    override fun generateSqlToDataFrame(sqlQuery: String, variableName: String): Code {
        // https://youtrack.jetbrains.com/issue/KTNB-1340/Support-DataFrames-as-a-DataSource-in-SQL-Cells
        TODO("Not yet implemented")
    }

    override fun preVariableName(hasOutput: Boolean, variableName: String): Code? {
        return if (hasOutput) Code(variableName) else null
    }

    override fun raiseException(message: String, canOmitTrace: Boolean): Code {
        if (canOmitTrace) return Code(JupyterKotlinCodeWrapper.raiseSqlTraceOmittingException(message))
        return Code(JupyterKotlinCodeWrapper.raiseException(message))
    }

    private fun shouldCreateUseStatement(packagePrefix: String, project: Project, notebookFile: BackedNotebookVirtualFile): Boolean {
        val compilerService = JupyterCompilerService.getForFile(project, notebookFile)
        return !compilerService.currentClasspath.any { file ->
            file.name.startsWith(packagePrefix)
        }
    }

    override fun addOrCreateTableDataFrame(
        variableName: String,
        data: ByteArray,
        isTableCreated: Boolean,
        project: Project,
        notebookFile: BackedNotebookVirtualFile,
    ): List<Code> {
        val duckDbDependency = """
            USE {
              dependencies("org.duckdb:duckdb_jdbc:$DUCKDB_JDBC_DRIVER_VERSION")
            }
        """.trimIndent()

        val dataframeUseStatement = "%use dataframe"
        val dataFrameCode = JupyterKotlinCodeWrapper.addOrCreateTableDataFrame(variableName, data, isTableCreated)
        val dataframeParts = listOfNotNull(
            dataframeUseStatement.takeIf { shouldCreateUseStatement("dataframe-core", project, notebookFile) },
            dataFrameCode
        )

        return buildList {
            if (shouldCreateUseStatement("duckdb_jdbc", project, notebookFile))
                add(duckDbDependency)

            add(dataframeParts.joinToString(System.lineSeparator()))
        }.map(::Code)
    }

    object JupyterKotlinCodeWrapper {

        /**
         * Wraps a string into Kotlin code that reconstructs it from Base64-encoded bytes.
         * This approach avoids issues with escape characters and special characters in generated code.
         *
         * @param text the string to encode and wrap
         * @return Kotlin code that decodes the Base64-encoded string at runtime
         */
        fun wrapString(text: String): String {
            val encodedString = Base64.getEncoder().encodeToString(text.toByteArray())
            return """String(java.util.Base64.getDecoder().decode("$encodedString"), kotlin.text.Charsets.UTF_8)"""
        }

        // All exceptions that are in the package 'org.jetbrains.kotlinx.jupyter' will have no stacktrace.
        //  See: com.intellij.kotlin.jupyter.core.jupyter.outputs.error.KotlinErrorOutputContentProvider
        fun raiseSqlTraceOmittingException(message: String): String =
            """throw org.jetbrains.kotlinx.jupyter.api.exceptions.ReplMessageOnlyException(${wrapString(message)})"""

        fun raiseException(errorMessage: String): String =
            """throw Exception(${wrapString(errorMessage)})"""

        fun generateCodeForDataFrame(data: ByteArray): String {
            val string = String(data, Charsets.UTF_8)
            return """
                import kotlin.io.path.deleteIfExists
                
                var df: org.jetbrains.kotlinx.dataframe.DataFrame<*>
                try {
                    val connection = java.sql.DriverManager.getConnection("jdbc:duckdb:$string")
                    df = org.jetbrains.kotlinx.dataframe.DataFrame.readAllSqlTables(connection).values.single()
                } finally {
                    try {
                        // deleting the file to clean up space immediately, avoiding disk usage increase in longer session.
                        kotlin.io.path.Path("$string").deleteIfExists()
                    } catch (_: Exception) {
                        // ignored
                    }
                }
                """.trimIndent()
        }

        private fun generateCodeSetDataFrameToVariable(variableName: String, data: ByteArray): String =
            """
                ${generateCodeForDataFrame(data)}
                val ${variableName}: org.jetbrains.kotlinx.dataframe.DataFrame<*> = df
            """.trimIndent()

        private fun generateCodeConcatWithDataFrame(variableName: String, data: ByteArray): String =
            """
                ${generateCodeForDataFrame(data)}
                val ${variableName} = ${variableName}.concat(df)
            """.trimIndent()

        fun addOrCreateTableDataFrame(variableName: String, data: ByteArray, isTableCreated: Boolean): String {
            return if (isTableCreated)
                generateCodeSetDataFrameToVariable(variableName, data)
            else
                generateCodeConcatWithDataFrame(variableName, data)
        }
    }

}
