// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.util.alsoIfNull
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlinx.jupyter.plugin.index.KotlinNotebookPermanentIndexService
import java.util.*

sealed interface KotlinNotebookDependencies {
    object All : KotlinNotebookDependencies
    class Selection(val values: Set<String>) : KotlinNotebookDependencies

    companion object {
        val None = Selection(emptySet())

        fun fromModules(modules: Collection<Module>) = Selection(modules.map { it.name }.toSortedSet())
        fun fromLibraries(libraries: Collection<Library>) = Selection(libraries.mapNotNull { it.name }.toSortedSet())
    }
}

internal class KotlinNotebookDependenciesProperty(name: String, defaultValue: KotlinNotebookDependencies) :
    KotlinNotebookProperty<KotlinNotebookDependencies>(name, defaultValue) {

    override fun JsonNode.toValue(): KotlinNotebookDependencies {
        if (this is BooleanNode)
            return if (this == BooleanNode.TRUE) KotlinNotebookDependencies.All else KotlinNotebookDependencies.None
        if (this !is ArrayNode) return KotlinNotebookDependencies.None

        val result = TreeSet<String>()
        for (i in 0 until size()) {
            result.add(this[i].asText())
        }
        return KotlinNotebookDependencies.Selection(result)
    }

    override fun KotlinNotebookDependencies.toNode(): JsonNode {
        return when (this) {
            KotlinNotebookDependencies.All -> BooleanNode.TRUE
            is KotlinNotebookDependencies.Selection -> ArrayNode(JsonNodeFactory(false)).also { arrayNode ->
                arrayNode.addAll(values.map { TextNode(it) })
            }
        }
    }
}