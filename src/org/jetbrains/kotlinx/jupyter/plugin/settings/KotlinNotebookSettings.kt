// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook

private val isBuildProjectProperty = KotlinNotebookBooleanProperty("isBuildProject", false)
private val isAddProjectLibrariesToClasspathProperty = KotlinNotebookBooleanProperty("isAddProjectLibrariesToClasspath", true)
private val projectDependenciesProperty = KotlinNotebookDependenciesProperty("projectDependencies", KotlinNotebookDependencies.None)
private val projectLibrariesProperty = KotlinNotebookDependenciesProperty("projectLibraries", KotlinNotebookDependencies.All)
private val sessionRunModeProperty = KotlinNotebookEnumProperty(
    name = "sessionRunMode",
    defaultValue = KotlinNotebookSessionRunMode.DEFAULT,
    kClass = KotlinNotebookSessionRunMode::class
)

var JupyterNotebook.isBuildProject by isBuildProjectProperty
var JupyterNotebook.isAddProjectLibrariesToClasspath by isAddProjectLibrariesToClasspathProperty
var JupyterNotebook.projectDependencies by projectDependenciesProperty
var JupyterNotebook.projectLibraries by projectLibrariesProperty
var JupyterNotebook.sessionRunMode by sessionRunModeProperty

data class KotlinNotebookSettings(
    val projectDependencies: KotlinNotebookDependencies,
    val projectLibraries: KotlinNotebookDependencies,
    val sessionRunMode: KotlinNotebookSessionRunMode,
) {
    companion object {
        val DEFAULT = KotlinNotebookSettings(
            projectDependenciesProperty.defaultValue,
            projectLibrariesProperty.defaultValue,
            sessionRunModeProperty.defaultValue,
        )
    }
}

fun KotlinNotebookSettings.asJson(): String? {
    if (this == KotlinNotebookSettings.DEFAULT) return null
    return JsonNodeFactory.instance.objectNode().also { node ->
        projectDependenciesProperty.writeValue(node, projectDependencies)
        projectLibrariesProperty.writeValue(node, projectLibraries)
    }.toPrettyString()
}

@RequiresEdt
fun JupyterNotebook.readSettings(): KotlinNotebookSettings {
    return KotlinNotebookSettings(
        projectDependencies = projectDependencies,
        projectLibraries = projectLibraries,
        sessionRunMode = sessionRunMode
    )
}

@RequiresEdt
internal fun JupyterNotebook.migrateSettings() {
    val isBuildProjectValue = isBuildProject
    if (isBuildProjectProperty.defaultValue != isBuildProjectValue) {
        isBuildProject = isBuildProjectProperty.defaultValue
        projectDependencies = if (isBuildProjectValue) KotlinNotebookDependencies.All else KotlinNotebookDependencies.None
    }
    val isAddLibrariesValue = isAddProjectLibrariesToClasspath
    if (isAddProjectLibrariesToClasspathProperty.defaultValue != isAddLibrariesValue) {
        isAddProjectLibrariesToClasspath = isAddProjectLibrariesToClasspathProperty.defaultValue
        projectLibraries = if (isAddLibrariesValue) KotlinNotebookDependencies.All else KotlinNotebookDependencies.None
    }
}
