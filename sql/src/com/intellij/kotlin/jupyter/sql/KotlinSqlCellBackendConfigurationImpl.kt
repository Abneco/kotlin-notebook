// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.sql

import com.intellij.database.extractors.DataExtractor
import com.intellij.database.psi.DbDataSource
import com.intellij.dataspell.jupyter.sql.common.SqlCellBackendConfiguration
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.connections.execution.core.JupyterExecutionCallback
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.kotlin.jupyter.core.jupyter.execution.kotlinNotebookCellExecutionCallbackFactory
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.project.Project

/**
 * Backend configuration for SQL cells in Kotlin notebooks.
 */
class KotlinSqlCellBackendConfigurationImpl : SqlCellBackendConfiguration {

    override fun isApplicable(notebook: JupyterNotebook): Boolean = notebook.isKotlinNotebook

    override suspend fun checkAndInstallLanguageDependencies(
        project: Project,
        notebookVirtualFile: BackedNotebookVirtualFile,
    ) {
        // There are no language dependencies for Kotlin SQL cells
    }

    override fun getExecutionCallbacks(project: Project, notebookVirtualFile: BackedNotebookVirtualFile): List<JupyterExecutionCallback> {
        return listOf(kotlinNotebookCellExecutionCallbackFactory.createUnboundCallback(project, notebookVirtualFile))
    }

    override fun getDataExtractor(dataSource: DbDataSource, project: Project): DataExtractor = DuckDBExtractor(project)

}
