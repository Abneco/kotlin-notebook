// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.sql

import com.intellij.database.Dbms
import com.intellij.database.data.types.SizeProvider
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.GridUtilCore
import com.intellij.database.dialects.DatabaseDialects
import com.intellij.database.extractors.DataExtractor
import com.intellij.database.extractors.DataExtractor.Extraction
import com.intellij.database.extractors.ExtractionConfig
import com.intellij.database.extractors.GridExtractorsUtilCore
import com.intellij.database.remote.jdbc.impl.UnparsedValue
import com.intellij.database.util.DdlBuilder
import com.intellij.database.util.Out
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException
import java.sql.Types
import java.util.LinkedList
import java.util.List
import java.util.Map
import kotlin.math.max

/**
 * An implementation of the [DataExtractor] interface that extracts data and processes it
 * into a DuckDB-compatible database format.
 *
 *
 * This extractor performs the following steps:
 * 
 *  * Creates a temporary DuckDB database file.
 *  * Connects to the database using the `org.duckdb.DuckDBDriver`.
 *  * Dynamically creates a table `tmp_table` based on the input column metadata.
 *  * Maps JDBC types to appropriate DuckDB types.
 *  * Inserts the provided row data into the table.
 *  * Writes the absolute path to the temporary database file to the output stream.
 * 
 * 
 * The temporary database file is set to be deleted on JVM exit.
 */
class DuckDBExtractor(val project: Project) : DataExtractor {
    override fun getFileExtension(): String {
        return "duckdb"
    }

    override fun supportsText(): Boolean {
        return false
    }

    override fun startExtraction(
        out: Out,
        allColumns: MutableList<out GridColumn>,
        query: String,
        config: ExtractionConfig,
        vararg selectedColumns: Int,
    ): Extraction {
        return object : Extraction {
            private val myRows: MutableList<GridRow> = LinkedList<GridRow>()
            private var myColumns: MutableList<out GridColumn> = List.copyOf(allColumns)
            private val mySelectedIndices: IntArray = if (selectedColumns.isNotEmpty())
                selectedColumns
            else
                GridExtractorsUtilCore.getNonEmptySelection(allColumns, selectedColumns)


            override fun updateColumns(columns: Array<GridColumn>) {
                myColumns = mutableListOf(*columns)
            }

            override fun addData(rows: MutableList<out GridRow>) {
                this.myRows.addAll(rows)
            }

            override fun complete() {
                if (myRows.isEmpty() && myColumns.isEmpty()) return
                val tempDb: Path
                try {
                    // 1. Create a temporary file for the DuckDB database and populate it
                    tempDb = retrieveDuckDBConnection(project) { _, _ ->
                        setupTableAndInsertData(this)
                    }

                    // 2. Write the path to the resulting database file to the output stream
                    // The consumer (e.g., a Jupyter kernel) will use this path to access the database.
                    val pathBytes = tempDb.toAbsolutePath().toString().toByteArray(StandardCharsets.UTF_8)
                    out.append(pathBytes)
                }
                catch (e: Exception) {
                    throw RuntimeException("Failed to extract data to DuckDB", e)
                }
            }

            /**
             * Sets up the target table in the DuckDB database and inserts all collected rows.
             * 
             * @param connection the JDBC connection to the temporary DuckDB database.
             * @throws SQLException if table creation or data insertion fails.
             */
            @Throws(SQLException::class)
            fun setupTableAndInsertData(connection: Connection) {
                // 1. Build CREATE TABLE statement with mapped types
                val sqlStringBuilder = StringBuilder()
                // We use Postgres dialect as a base for DdlBuilder as the DuckDB dialect is based on Postgres and
                // the changes there shouldn't matter in our use case.
                // See https://duckdb.org/docs/current/sql/dialect/overview for more information.
                val builder: DdlBuilder = DdlBuilder(sqlStringBuilder).qualifyReferences(true)
                    .withDialect(DatabaseDialects.findByDbms(Dbms.POSTGRES)!!)

                builder.keyword("CREATE").space().keyword("TABLE").space().identifier("tmp_table").space().symbol("(").newLine()
                var first = true
                for (idx in mySelectedIndices) {
                    val column: GridColumn = myColumns.get(idx)
                    if (GridUtilCore.isRowId(column)) continue

                    if (!first) builder.symbol(",").newLine()
                    val typeName = getDuckDBType(column)
                    builder.space(2).columnRef(column.getName()).space().type(typeName)
                    first = false
                }
                builder.newLine().symbol(")").symbol(";").newLine()

                connection.createStatement().use { statement ->
                    statement.execute(sqlStringBuilder.toString())
                }
                // 2. Build and execute INSERT statement
                builder.clear()
                builder.keyword("INSERT").space().keyword("INTO").space().identifier("tmp_table").space().keyword("VALUES").space()
                    .symbol("(")
                for (i in mySelectedIndices.indices) {
                    if (i > 0) builder.symbol(",")
                    builder.symbol("?")
                }
                builder.symbol(")").symbol(";").newLine()

                connection.prepareStatement(sqlStringBuilder.toString()).use { pstmt ->
                    for (row in myRows) {
                        var paramIdx = 1
                        for (idx in mySelectedIndices) {
                            var value = myColumns.get(idx).getValue(row)

                            // Handle specific value types for better DuckDB compatibility
                            when (value) {
                                is UnparsedValue -> {
                                    value = value.stringRepresentation
                                }
                                is Array<*> -> {
                                    value = parseArrayToSingleString(value)
                                }
                                is MutableMap<*, *>, is MutableCollection<*> -> {
                                    value = value.toString()
                                }
                            }
                            pstmt.setObject(paramIdx++, value)
                        }
                        pstmt.addBatch()
                    }
                    pstmt.executeBatch()
                }
            }

            fun parseArrayToSingleString(arr: Array<*>): String {
                val body = arr
                    .map { e: Any? ->
                        if (e is Array<*>) {
                            return@map parseArrayToSingleString(e)
                        }
                        e.toString()
                    }.joinToString(",")
                return "[$body]"
            }

            /**
             * Maps JDBC types and specific database-native type names from [GridColumn] to DuckDB SQL types.
             * Defaults to `VARCHAR` if no direct mapping is found.
             * 
             * @param column the grid column containing type information.
             * @return the corresponding DuckDB type name.
             */
            fun getDuckDBType(column: GridColumn): String {
                val type = column.getType()
                val typeName = if (column.getTypeName() != null) column.getTypeName()!!.lowercase() else ""

                // First, try to resolve the duckdb type by type name overrides
                val duckDBTypeByTypeName: String? = typeNameToDuckDbTypeMap[typeName]
                if (duckDBTypeByTypeName != null) return duckDBTypeByTypeName

                // Second, try to resolve the duckdb type by database plugin type
                return when (type) {
                    Types.BIT, Types.BOOLEAN -> "BOOLEAN"
                    Types.TINYINT -> "UTINYINT"
                    Types.SMALLINT -> "SMALLINT"
                    Types.INTEGER -> "INTEGER"
                    Types.BIGINT -> "BIGINT"
                    Types.FLOAT, Types.REAL -> "FLOAT"
                    Types.DOUBLE -> "DOUBLE"
                    Types.DECIMAL, Types.NUMERIC -> {
                        if (column is SizeProvider) {
                            val precision = column.getSize()
                            val scale = max(column.getScale(), 0)
                            if (precision > 0) {
                                return "DECIMAL($precision, $scale)"
                            }
                        }
                        "DECIMAL"
                    }
                    Types.DATE -> "DATE"
                    Types.TIME -> "TIME"
                    Types.TIMESTAMP -> "TIMESTAMP"
                    Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> "BLOB"
                    else -> "VARCHAR"
                }
            }
        }
    }

    companion object {
        // Explicit DuckDB type-name overrides for cases where JDBC column types are too generic.
        // Extend this map as new type names need DuckDB-specific handling.
        private val typeNameToDuckDbTypeMap: MutableMap<String, String> = Map.of<String, String>(
            "json", "JSON",
            "jsonb", "JSON",
            "uuid", "UUID",
            "interval", "INTERVAL",
            "timestamptz", "TIMESTAMPTZ",
            "timetz", "TIMETZ"
        )
    }
}
