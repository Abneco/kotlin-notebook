// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

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

internal class KotlinNotebookProperty(val name: @Nls String, val defaultValue: Boolean) : ReadWriteProperty<JupyterNotebook, Boolean> {
    override fun getValue(thisRef: JupyterNotebook, property: KProperty<*>): Boolean {
        val metadata = thisRef.getMetadata(METADATA_KEY) ?: return defaultValue
        val propertyMetadata = metadata.get(name) ?: return defaultValue
        return propertyMetadata == BooleanNode.TRUE
    }

    override fun setValue(thisRef: JupyterNotebook, property: KProperty<*>, value: Boolean) {
        if (value == defaultValue) {
            removeValue(thisRef)
            return
        }
        val metadata = thisRef.getMetadata(METADATA_KEY) as? ObjectNode
            ?: JsonNodeFactory.instance.objectNode().also { thisRef.setMetadata(METADATA_KEY, it) }
        metadata.set<ObjectNode>(name, if (value) BooleanNode.TRUE else BooleanNode.FALSE)
        thisRef.notifyListeners()
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