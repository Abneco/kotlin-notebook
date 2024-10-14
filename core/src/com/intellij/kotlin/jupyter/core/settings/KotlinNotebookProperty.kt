// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.jupyter.core.jupyter.nbformat.notifyNotebookChanged
import org.jetbrains.annotations.Nls
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

internal const val METADATA_KEY = "ktnbPluginMetadata"

internal abstract class KotlinNotebookProperty<T>(val name: String, val defaultValue: T) : ReadWriteProperty<JupyterNotebook, T> {
    protected abstract fun JsonNode.toValue(): T
    protected abstract fun T.toNode(): JsonNode

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
        thisRef.notifyNotebookChanged()
    }

    private fun doWriteValue(metadata: ObjectNode, value: T) {
        metadata.set<ObjectNode>(name, value.toNode())
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
            thisRef.notifyNotebookChanged()
        }
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

internal class KotlinNotebookEnumProperty<T : Enum<T>>(
    name: @Nls String,
    defaultValue: T,
    private val kClass: KClass<T>,
) : KotlinNotebookProperty<T>(name, defaultValue) {
    override fun JsonNode.toValue(): T {
        return this.textValue()?.let { value ->
            kClass.java.enumConstants.find { it.name.equals(value, ignoreCase = true) }
        } ?: defaultValue
    }

    override fun T.toNode(): JsonNode {
        return TextNode(name)
    }
}
