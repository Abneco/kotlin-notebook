// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.application.runInEdt
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterNotebook
import org.jetbrains.plugins.notebooks.jupyter.nbformat.NotebookChanged
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private const val METADATA_KEY = "ktnbPluginMetadata"

internal abstract class KotlinNotebookProperty<T>(val name: String, val defaultValue: T) : ReadWriteProperty<JupyterNotebook, T> {
    abstract fun JsonNode.toValue(): T
    abstract fun T.toNode(): JsonNode

    override fun getValue(thisRef: JupyterNotebook, property: KProperty<*>): T {
        val metadata = thisRef.getMetadata(METADATA_KEY) ?: return defaultValue
        val propertyMetadata = metadata.get(name) ?: return defaultValue
        return propertyMetadata.toValue()
    }

    override fun setValue(thisRef: JupyterNotebook, property: KProperty<*>, value: T) {
        if (value == defaultValue) {
            removeValue(thisRef)
            return
        }
        val metadata = thisRef.getMetadata(METADATA_KEY) as? ObjectNode
            ?: JsonNodeFactory.instance.objectNode().also { thisRef.setMetadata(METADATA_KEY, it) }
        doWriteValue(metadata, value)
        thisRef.notifyListeners()
    }

    private fun doWriteValue(metadata: ObjectNode, value: T) {
        metadata.set<ObjectNode>(name, value.toNode())
    }

    internal fun writeValue(metadata: ObjectNode, value: T) {
        if (value == defaultValue) return
        doWriteValue(metadata, value)
    }

    private fun removeValue(thisRef: JupyterNotebook) {
        val metadata = thisRef.getMetadata(METADATA_KEY) ?: return
        if (metadata !is ObjectNode) {
            thisRef.removeMetadata(METADATA_KEY)
            return
        }
        metadata.remove(name)
        if (metadata.isEmpty) {
            thisRef.removeMetadata(METADATA_KEY)
        } else {
            thisRef.notifyListeners()
        }
    }

    private fun JupyterNotebook.notifyListeners() {
        runInEdt { getJupyterChangeListeners().forEach { it.onEvent(NotebookChanged(this)) } }
    }
}

internal class KotlinNotebookBooleanProperty(name: @Nls String, defaultValue: Boolean) : KotlinNotebookProperty<Boolean>(name, defaultValue) {
    override fun JsonNode.toValue(): Boolean {
        return this == BooleanNode.TRUE
    }

    override fun Boolean.toNode(): JsonNode {
        return if (this) BooleanNode.TRUE else BooleanNode.FALSE
    }
}
