// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.cells

import com.intellij.jupyter.core.jackson
import com.intellij.jupyter.core.jupyter.nbformat.JupyterCell
import kotlin.reflect.KProperty

/**
 * Marker interface for storing metadata related to a cell which is accessed by a specific [StorableCellMetadataKey]
 */
interface StorableCellMetadata

/**
 * Key for associating a specific [StorableCellMetadata] with a [JupyterCell]
 */
class StorableCellMetadataKey<T : StorableCellMetadata>(
    val metadataKey: String,
    val valueClass: Class<T>
)

/**
 * Reads [T] metadata from [JupyterCell.metadata] by a specific [key]
 */
fun <T : StorableCellMetadata> JupyterCell.readCellMetadata(key: StorableCellMetadataKey<T>): T? {
    val jsonNode = metadata.getJsonNode(key.metadataKey) ?: return null
    return jackson.treeToValue(jsonNode, key.valueClass)
}

/**
 * Writes [T] metadata to [JupyterCell.metadata]
 */
fun <T : StorableCellMetadata> JupyterCell.writeCellMetadata(key: StorableCellMetadataKey<T>, value: T) {
    metadata.setJsonNode(key.metadataKey, jackson.valueToTree(value))
}

fun <T: StorableCellMetadata> cellMetadataAccessor(key: StorableCellMetadataKey<T>): CellMetadataPropertyDelegate<T> =
    CellMetadataPropertyDelegate(key)

/**
 * Delegate class for creating handful accessors for [StorableCellMetadata]
 */
class CellMetadataPropertyDelegate<T : StorableCellMetadata>(
    private val key: StorableCellMetadataKey<T>,
) {
    operator fun getValue(cell: JupyterCell, property: KProperty<*>): T? {
        return cell.readCellMetadata(key)
    }

    operator fun setValue(cell: JupyterCell, property: KProperty<*>, value: T?) {
        if (value == null) {
            cell.metadata.remove(key.metadataKey)
        } else {
            cell.writeCellMetadata(key, value)
        }
    }
}
