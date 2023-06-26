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
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.util.alsoIfNull
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlinx.jupyter.plugin.index.KotlinNotebookPermanentIndexService
import java.util.*

sealed interface KotlinNotebookDependencies {
    object All : KotlinNotebookDependencies
    data class Selection(val values: Set<String>) : KotlinNotebookDependencies

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

fun KotlinNotebookDependencies.findModules(project: Project): List<Module> {
    return when (this) {
        KotlinNotebookDependencies.All -> getSuitableModules(project)
        is KotlinNotebookDependencies.Selection -> values.mapNotNull {
            ModuleManager.getInstance(project).findModuleByName(it).alsoIfNull {
                thisLogger<KotlinNotebookDependencies>().warn("Could not find module by name $it in project ${project.name}")
            }
        }
    }
}

/**
 * Returns a list of modules from the given project which are suitable as a Kotlin Notebook dependency.
 * Modules with Kotlin or Java sdk and without "buildSrc" in the name are considered suitable.
 *
 * @param project the project to get the modules for.
 * @return a `List` of suitable `Module` objects for the given project.
 */
internal fun getSuitableModules(project: Project): List<Module> {
    fun Module.isProbablyBuildSrc() = name.split(".").any { it == "buildSrc" }

    return ModuleManager.getInstance(project).modules.filter {
        if (it.isProbablyBuildSrc()) return@filter false
        val sdk = ModuleRootManager.getInstance(it).sdk ?: return@filter false
        sdk.sdkType == KotlinSdkType.INSTANCE || sdk.sdkType is JavaSdkType
    }
}

fun KotlinNotebookDependencies.findLibraries(project: Project): List<Library> {
    if (isEmpty()) return emptyList()

    val allLibraries = getSuitableLibraries(project)
    if (this is KotlinNotebookDependencies.Selection) {
        return allLibraries.filter { values.contains(it.name) }
    }
    return allLibraries
}

/**
 * Returns a list of libraries from the given project that are suitable for use in a Kotlin Notebook.
 * This list excludes a "Permanent Script Dependencies" and unnamed libraries
 * (technically, a module-level library can have an empty name).
 *
 * @param project the project to get the libraries for.
 * @return a `List` of suitable `Library` objects for the given project.
 */
internal fun getSuitableLibraries(project: Project): List<Library> {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.filter {
        it.name != KotlinNotebookPermanentIndexService.SCRIPT_DEPENDENCIES_LIBRARY_NAME && it.name != null
    }
}

fun KotlinNotebookDependencies.isEmpty(): Boolean {
    if (this is KotlinNotebookDependencies.Selection) return values.isEmpty()
    return false
}

fun KotlinNotebookDependencies.isAffectedBy(dependencies: KotlinNotebookDependencies): Boolean {
    if (this == KotlinNotebookDependencies.None || dependencies == KotlinNotebookDependencies.None) return false
    if (this == KotlinNotebookDependencies.All || dependencies == KotlinNotebookDependencies.All) return true
    return (this as KotlinNotebookDependencies.Selection).values.any { (dependencies as KotlinNotebookDependencies.Selection).values.contains(it) }
}