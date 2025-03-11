// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.editor.handlers.TableDataFileDropHandlerContext
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.openapi.project.Project


private const val GEOJSON_EXTENSION = "geojson"
private const val SHAPE_EXTENSION = "shp"


class KotlinGeoDataDropHandler : AbstractKotlinDataframeDropHandler(
    KotlinNotebookBundle.message("kotlin.jupyter.editor.dnd.geo.dataframe.command"),
    setOf(
        GEOJSON_EXTENSION,
        SHAPE_EXTENSION
    )
) {
    override fun generateImportExpression(dataFilePath: String, context: TableDataFileDropHandlerContext): String {
        return if (dataFilePath.endsWith(GEOJSON_EXTENSION)) {
            "GeoDataFrame.readGeoJson(\"$dataFilePath\")"
        } else {
            "GeoDataFrame.readShapefile(\"$dataFilePath\")"
        }
    }

    override fun generateCellCode(context: TableDataFileDropHandlerContext): String {
        val dataFilePath = context.resolveFilePath()
        val dfName = context.dataframeName ?: nameSuggester.createDataframeName(context.projectOrNull, context.dataFileNameWithoutExtension)
        val importExpression = generateImportExpression(dataFilePath, context)

        val isGeoDataframeInClassPath = when (val pathData = context.pathData) {
            is TableDataFileDropHandlerContext.PathData.FileBased -> isGeoDataFrameInClasspath(pathData.notebookFile, pathData.project)
            is TableDataFileDropHandlerContext.PathData.Lightweight -> false
        }

        return listOfNotNull(
            "%use dataframe(enableExperimentalGeo=true)\n".takeIf { !isGeoDataframeInClassPath },
            """
            val $dfName = $importExpression
            $dfName.df
            """.trimIndent()
        ).joinToString("")
    }

    private fun isGeoDataFrameInClasspath(notebookFile: BackedNotebookVirtualFile, project: Project): Boolean {
        val compilerService = JupyterCompilerService.getForFile(project, notebookFile)
        return compilerService.currentClasspath.any { file ->
            file.name.startsWith("dataframe-geo")
        }
    }
}
