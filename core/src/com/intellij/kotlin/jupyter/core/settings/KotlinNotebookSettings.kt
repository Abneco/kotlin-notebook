// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.util.concurrency.annotations.RequiresEdt

private val isBuildProjectProperty = KotlinNotebookBooleanProperty("isBuildProject", false)
private val isAddProjectLibrariesToClasspathProperty = KotlinNotebookBooleanProperty("isAddProjectLibrariesToClasspath", true)
private val notebookDependenciesProperty = KotlinNotebookDependenciesProperty(
    projectDependenciesPropertyName = "projectDependencies",
    projectLibrariesPropertyName = "projectLibraries",
)
private val sessionRunModeProperty = KotlinNotebookEnumProperty(
    name = "sessionRunMode",
    defaultValue = KotlinNotebookSessionRunMode.DEFAULT,
    kClass = KotlinNotebookSessionRunMode::class
)

var JupyterNotebook.isBuildProject by isBuildProjectProperty
var JupyterNotebook.isAddProjectLibrariesToClasspath by isAddProjectLibrariesToClasspathProperty
var JupyterNotebook.notebookDependencies by notebookDependenciesProperty
var JupyterNotebook.sessionRunMode by sessionRunModeProperty

data class KotlinNotebookSettings(
    val notebookDependencies: KotlinNotebookDependencies,
    val sessionRunMode: KotlinNotebookSessionRunMode,
) {
    companion object {
        val DEFAULT = KotlinNotebookSettings(
            KotlinNotebookDependenciesProperty.defaultValue,
            sessionRunModeProperty.defaultValue,
        )
    }
}

fun KotlinNotebookSettings.asJson(): String? {
    if (this == KotlinNotebookSettings.DEFAULT) return null
    return JsonNodeFactory.instance.objectNode().also { node ->
        notebookDependenciesProperty.writeValue(node, notebookDependencies)
    }.toPrettyString()
}

@RequiresEdt
fun JupyterNotebook.readSettings(): KotlinNotebookSettings {
    return KotlinNotebookSettings(
        notebookDependencies = notebookDependencies,
        sessionRunMode = sessionRunMode
    )
}

@RequiresEdt
internal fun JupyterNotebook.migrateSettings() {
    // we do not support depending on all modules in the project anymore
    val isBuildProjectValue = isBuildProject
    if (isBuildProjectProperty.defaultValue != isBuildProjectValue) {
        isBuildProject = isBuildProjectProperty.defaultValue
    }
    val isAddLibrariesValue = isAddProjectLibrariesToClasspath
    if (isAddProjectLibrariesToClasspathProperty.defaultValue != isAddLibrariesValue) {
        isAddProjectLibrariesToClasspath = isAddProjectLibrariesToClasspathProperty.defaultValue
        notebookDependencies = if (isAddLibrariesValue) KotlinNotebookDependencies.AllLibraries else KotlinNotebookDependencies.None
    }
}
