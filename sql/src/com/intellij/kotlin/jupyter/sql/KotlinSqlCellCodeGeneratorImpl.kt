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

    private fun shouldCreateUseStatement(project: Project, notebookFile: BackedNotebookVirtualFile): Boolean {
        val compilerService = JupyterCompilerService.getForFile(project, notebookFile)
        return !compilerService.currentClasspath.any { file ->
            file.name.startsWith("dataframe-core")
        }
    }

    override fun addOrCreateTableDataFrame(
        variableName: String,
        data: ByteArray,
        isTableCreated: Boolean,
        project: Project,
        notebookFile: BackedNotebookVirtualFile,
    ): Code {
        return Code(listOfNotNull(
            "%use dataframe".takeIf { shouldCreateUseStatement(project, notebookFile) },
            JupyterKotlinCodeWrapper.addOrCreateTableDataFrame(variableName, data, isTableCreated)
        ).joinToString(System.lineSeparator()))
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

        /**
         * Wraps a UTF-8 encoded byte array into Kotlin code that reconstructs it from Base64-encoded bytes.
         * This approach avoids issues with escape characters and special characters in generated code.
         *
         * @param utf8encoded the UTF-8 encoded byte array to encode and wrap
         * @return Kotlin code that decodes the Base64-encoded string at runtime
         */
        fun wrapString(utf8encoded: ByteArray): String {
            val encodedString = Base64.getEncoder().encodeToString(utf8encoded)
            return """String(java.util.Base64.getDecoder().decode("$encodedString"), kotlin.text.Charsets.UTF_8)"""
        }

        // All exceptions that are in the package 'org.jetbrains.kotlinx.jupyter' will have no stacktrace.
        //  See: com.intellij.kotlin.jupyter.core.jupyter.outputs.error.KotlinErrorOutputContentProvider
        fun raiseSqlTraceOmittingException(message: String): String =
            """throw org.jetbrains.kotlinx.jupyter.api.exceptions.ReplMessageOnlyException(${wrapString(message)})"""

        fun raiseException(errorMessage: String): String =
            """throw Exception(${wrapString(errorMessage)})"""

        fun generateCodeForDataFrame(data: ByteArray): String =
            """org.jetbrains.kotlinx.dataframe.DataFrame.readCsv(${wrapString(data)}.byteInputStream(), delimiter = ',')"""

        private fun generateCodeSetDataFrameToVariable(variableName: String, data: ByteArray): String =
            """val ${variableName}: DataFrame<*> = ${generateCodeForDataFrame(data)}"""

        private fun generateCodeConcatWithDataFrame(variableName: String, data: ByteArray): String =
            """val ${variableName}=${variableName}.concat(${generateCodeForDataFrame(data)})"""

        fun addOrCreateTableDataFrame(variableName: String, data: ByteArray, isTableCreated: Boolean): String {
            return if (isTableCreated)
                generateCodeSetDataFrameToVariable(variableName, data)
            else
                generateCodeConcatWithDataFrame(variableName, data)
        }
    }

}
