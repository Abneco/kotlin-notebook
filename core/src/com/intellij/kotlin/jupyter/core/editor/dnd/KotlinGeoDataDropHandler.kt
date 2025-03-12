// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.dnd

import com.intellij.jupyter.core.editor.handlers.TableDataFileDropHandlerContext
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import java.io.File


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

    override fun isTargetLibrary(file: File): Boolean {
        return file.name.startsWith("dataframe-geo")
    }

    override fun getUseStatement(): String {
        return "%use dataframe(enableExperimentalGeo=true)\n"
    }
}
