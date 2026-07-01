// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.sql

import com.intellij.dataspell.jupyter.sql.common.SqlCellCommonConfiguration
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.lang.LanguageNamesValidation
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinLanguage

/**
 * Common configuration for SQL cells in Kotlin notebooks.
 */
class KotlinSqlCellCommonConfigurationImpl : SqlCellCommonConfiguration {

    override fun isApplicable(notebook: JupyterNotebook): Boolean = notebook.isKotlinNotebook

    override fun isEnabled(): Boolean = sqlCellsEnabled

    // There are no other SQL implementations in kotlin notebooks
    override fun shouldLoadAsNativeSqlCells(notebook: JupyterNotebook): Boolean = true

    override fun isValidVariableName(value: String, project: Project?): Boolean {
        val validator = LanguageNamesValidation.INSTANCE.forLanguage(KotlinLanguage.INSTANCE)
        return validator.isIdentifier(value, project)
    }

    // https://youtrack.jetbrains.com/issue/KTNB-1340/Support-DataFrames-as-a-DataSource-in-SQL-Cells
    override fun allowDataFrameAsDataSource(): Boolean = false

    override fun getVariablePrefix(): String = "dfSql"
}
