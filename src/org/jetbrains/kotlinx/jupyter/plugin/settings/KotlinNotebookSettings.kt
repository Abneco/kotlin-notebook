// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterNotebook

private val isBuildProjectProperty = KotlinNotebookProperty("isBuildProject", false)
private val isAddProjectLibrariesToClasspathProperty = KotlinNotebookProperty("isAddProjectLibrariesToClasspath", false)

var JupyterNotebook.isBuildProject by isBuildProjectProperty
var JupyterNotebook.isAddProjectLibrariesToClasspath by isAddProjectLibrariesToClasspathProperty

data class KotlinNotebookSettings(val isBuildProject: Boolean, val isAddProjectLibrariesToClasspath: Boolean) {
    companion object {
        val DEFAULT = KotlinNotebookSettings(isBuildProjectProperty.defaultValue, isAddProjectLibrariesToClasspathProperty.defaultValue)
    }
}

@RequiresEdt
fun JupyterNotebook.readSettings(): KotlinNotebookSettings {
    return KotlinNotebookSettings(isBuildProject, isAddProjectLibrariesToClasspath)
}

@RequiresEdt
fun JupyterNotebook.writeSettings(settings: KotlinNotebookSettings) {
    isBuildProject = settings.isBuildProject
    isAddProjectLibrariesToClasspath = settings.isAddProjectLibrariesToClasspath
}