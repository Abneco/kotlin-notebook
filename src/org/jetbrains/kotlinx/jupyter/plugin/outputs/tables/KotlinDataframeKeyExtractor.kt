//// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
//package org.jetbrains.kotlinx.jupyter.plugin.outputs.tables
//
//import com.fasterxml.jackson.databind.node.ObjectNode
//import com.intellij.util.asSafely
//import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.NotebookSwingOutputDataKeyExtractor
//import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputDataKey
//
//class KotlinDataframeKeyExtractor: NotebookSwingOutputDataKeyExtractor {
//    override fun extractKey(dataObject: ObjectNode, executionCount: Int?): NotebookOutputDataKey? {
//        if (!dataObject.has(jsonPayloadField)) return null
//        val jsonPayload = dataObject[jsonPayloadField].asSafely<ObjectNode>() ?: return null
//        if (!jsonPayload.has(serializedDataframeField)) return null
//        val serializedDataframe = jsonPayload[serializedDataframeField].asSafely<ObjectNode>() ?: return null
//        return KotlinDataframeOutputDataKey(serializedDataframe, executionCount)
//    }
//
//    companion object {
//        private const val jsonPayloadField = "application/json"
//        private const val serializedDataframeField = "dataframe"
//    }
//}