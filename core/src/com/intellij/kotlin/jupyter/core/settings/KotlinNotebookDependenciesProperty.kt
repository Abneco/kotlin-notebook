// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.jupyter.core.jupyter.nbformat.notifyNotebookChanged
import com.intellij.kotlin.jupyter.core.projectModel.KotlinNotebookPermanentIndexService
import com.intellij.kotlin.jupyter.core.projectModel.extensions.KotlinNotebookSessionLibrariesFilter
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.kotlin.idea.base.projectStructure.hasProductionSource
import org.jetbrains.kotlin.idea.base.projectStructure.hasTestSource
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class KotlinNotebookDependencies {
    data object None : KotlinNotebookDependencies()

    data object AllLibraries : KotlinNotebookDependencies()

    data class SingleModule(val moduleName: String) : KotlinNotebookDependencies() {
        constructor(module: Module) : this(module.name)
    }
}

internal class KotlinNotebookDependenciesProperty(
    private val projectDependenciesPropertyName: String,
    private val projectLibrariesPropertyName: String,
) : ReadWriteProperty<JupyterNotebook, KotlinNotebookDependencies> {
    companion object {
        val defaultValue: KotlinNotebookDependencies = KotlinNotebookDependencies.None
    }

    override fun getValue(thisRef: JupyterNotebook, property: KProperty<*>): KotlinNotebookDependencies {
        val metadata = thisRef.getMetadata(METADATA_KEY) ?: return defaultValue
        return toValue(
            projectDependenciesNode = metadata[projectDependenciesPropertyName],
            projectLibrariesNode = metadata[projectLibrariesPropertyName],
        )
    }

    override fun setValue(thisRef: JupyterNotebook, property: KProperty<*>, value: KotlinNotebookDependencies) {
        val metadata = thisRef.getMetadata(METADATA_KEY) as? ObjectNode
            ?: JsonNodeFactory.instance.objectNode().also { thisRef.setMetadata(METADATA_KEY, it) }
        writeValue(metadata, value)
        if (metadata.isEmpty) {
            thisRef.removeMetadata(METADATA_KEY)
        }
        thisRef.notifyNotebookChanged()
    }

    internal fun writeValue(metadata: ObjectNode, value: KotlinNotebookDependencies) {
        val nodes = value.toNodes()
        writeNode(metadata, projectDependenciesPropertyName, nodes.projectDependenciesNode)
        writeNode(metadata, projectLibrariesPropertyName, nodes.projectLibrariesNode)
    }

    private fun writeNode(metadata: ObjectNode, name: String, node: JsonNode?) {
        if (node != null) {
            metadata.set(name, node)
        } else {
            metadata.remove(name)
        }
    }

    private fun projectLibrariesUsed(projectLibrariesNode: JsonNode?): Boolean {
        if (projectLibrariesNode == null) return defaultValue is KotlinNotebookDependencies.AllLibraries
        if (projectLibrariesNode is BooleanNode) return projectLibrariesNode == BooleanNode.TRUE
        if (projectLibrariesNode !is ArrayNode) return false
        return projectLibrariesNode.size() > 0
    }

    private fun toValue(projectDependenciesNode: JsonNode?, projectLibrariesNode: JsonNode?): KotlinNotebookDependencies {
        val fallbackValue = if (projectLibrariesUsed(projectLibrariesNode)) {
            KotlinNotebookDependencies.AllLibraries
        } else {
            KotlinNotebookDependencies.None
        }

        if (projectDependenciesNode is BooleanNode) {
            // we do not support depending on multiple modules anymore
            return fallbackValue
        }
        if (projectDependenciesNode !is ArrayNode) return fallbackValue
        // we do not support depending on multiple modules anymore
        if (projectDependenciesNode.size() > 1) return fallbackValue

        // possibly discarding libraries, as we do not support depending on a module and on libraries simultaneously
        return KotlinNotebookDependencies.SingleModule(projectDependenciesNode[0].asText())
    }

    private class Nodes(
        val projectDependenciesNode: JsonNode?,
        val projectLibrariesNode: JsonNode?,
    )

    private fun KotlinNotebookDependencies.toNodes(): Nodes {
        return when (this) {
            defaultValue -> Nodes(
                projectDependenciesNode = null,
                projectLibrariesNode = null
            )
            KotlinNotebookDependencies.AllLibraries -> Nodes(
                projectDependenciesNode = null,
                projectLibrariesNode = BooleanNode.TRUE,
            )
            KotlinNotebookDependencies.None -> Nodes(
                projectDependenciesNode = null,
                projectLibrariesNode = BooleanNode.FALSE,
            )
            is KotlinNotebookDependencies.SingleModule -> Nodes(
                projectDependenciesNode = ArrayNode(JsonNodeFactory(false)).also { arrayNode ->
                    arrayNode.add(TextNode(moduleName))
                },
                projectLibrariesNode = BooleanNode.FALSE,
            )
        }
    }
}

fun KotlinNotebookDependencies.findModule(project: Project): Module? {
    if (this !is KotlinNotebookDependencies.SingleModule) return null
    return ModuleManager.getInstance(project).findModuleByName(moduleName) ?: run {
        thisLogger<KotlinNotebookDependencies>().warn("Could not find module by name $moduleName in project ${project.name}")
        null
    }
}

/**
 * Returns a list of modules from the given project which are suitable as a Kotlin Notebook dependency.
 * Modules with Kotlin or Java sdk and without "buildSrc" in the name are considered suitable.
 *
 * @param project the project to get the modules for.
 * @return a `List` of suitable `Module` objects for the given project.
 */
internal fun getSuitableModules(project: Project): Iterable<Module> {
    fun Module.isProbablyBuildSrc() = name.split(".").any { it == "buildSrc" }

    return ModuleManager.getInstance(project).modules.filter {
        if (it.isProbablyBuildSrc()) return@filter false
        val sdk = ModuleRootManager.getInstance(it).sdk ?: return@filter false
        val hasSdk = sdk.sdkType is JavaSdkType // Kotlin MPP JVM source set will have JavaSdkType too
        val hasSources = it.hasProductionSource || it.hasTestSource
        hasSdk && hasSources
    }.sortedBy { it.name }
}

/**
 * Returns a list of libraries from the given project that are suitable for use in a Kotlin Notebook.
 * This list excludes "Permanent Script Dependencies", special libraries with backend artifacts and unnamed libraries
 * (technically, a module-level library can have an empty name).
 *
 * @param project the project to get the libraries for.
 * @return a `List` of suitable `Library` objects for the given project.
 */
internal fun getSuitableLibraries(project: Project): List<Library> {
    val projectLibraries = LibraryTablesRegistrar.getInstance()
        .getLibraryTable(project).libraries

    val librariesCandidates = projectLibraries.filter {
        it.name != KotlinNotebookPermanentIndexService.SCRIPT_DEPENDENCIES_LIBRARY_NAME && it.name != null
    }
    return KotlinNotebookSessionLibrariesFilter.filterSessionLibraries(project, librariesCandidates)
}

fun KotlinNotebookDependencies.isAffectedBy(changedLibrary: Library?, changedModules: Collection<Module>): Boolean {
    if (changedLibrary == null && changedModules.isEmpty()) return false
    return when (this) {
        KotlinNotebookDependencies.None -> false
        KotlinNotebookDependencies.AllLibraries -> changedLibrary != null
        is KotlinNotebookDependencies.SingleModule -> changedModules.any { it.name == moduleName }
    }
}
