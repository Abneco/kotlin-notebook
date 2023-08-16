// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.resources.ArtifactDescription
import org.jetbrains.kotlinx.jupyter.plugin.resources.toIntellijModelDescription

class MavenVersionComboBox(
    private val project: Project,
    private val libraryDescription: ArtifactDescription,
) : ComboBox<String>() {
    private var state = State.NOT_LOADED

    var version: String
        get() {
            return when(state) {
                State.NOT_LOADED -> ""
                State.LOADED -> (selectedItem as String?).orEmpty()
            }
        }
        set(@NlsSafe value) {
            if (state == State.LOADED) {
                selectedItem = value
            }
        }

    val isLoaded get() = state == State.LOADED

    init {
        reloadVersionsAsync()
    }

    private fun reloadVersionsAsync() {
        val promise = JarRepositoryManager.getAvailableVersions(project, libraryDescription.toIntellijModelDescription())
        promise.onSuccess(::initializeComboBox)
    }

    private fun initializeComboBox(versions: Collection<String>) {
        val currentVersion = KotlinNotebookProjectOptionsProvider.getInstance(project).kernelVersion
        val allVersions = buildSet {
            addAll(versions)
            add(currentVersion)
        }.toMutableList().apply {
            sortByDescending { versionString ->
                if (versionString != null) KotlinKernelVersion.fromMavenVersion(versionString) ?: EMPTY_VERSION
                else EMPTY_VERSION
            }
        }

        val versionSelectorModel = CollectionComboBoxModel<String>()
        versionSelectorModel.add(allVersions)
        versionSelectorModel.selectedItem = currentVersion

        setModel(versionSelectorModel)
        state = State.LOADED
    }

    private enum class State {
        NOT_LOADED, LOADED
    }
}

private val EMPTY_VERSION = KotlinKernelVersion.from(0, 0, 0)

fun KotlinKernelVersion.Companion.fromMavenVersion(string: String): KotlinKernelVersion? {
    val components = string.split(SEP)
    if (components.size != 3) return null

    val intComponents = mutableListOf<Int>()
    for (i in 0..1) {
        intComponents.add(components[i].toIntOrNull() ?: return null)
    }

    val lastComponent = components[2]
    val lastIntComponents = lastComponent.split(DEV_SEP)
    for (component in lastIntComponents) {
        intComponents.add(component.toIntOrNull() ?: return null)
    }

    val major = intComponents[0]
    val minor = intComponents[1]
    val micro = intComponents[2]
    val build = intComponents.elementAtOrNull(3)
    val dev = intComponents.elementAtOrNull(4)

    return from(major, minor, micro, build, dev)
}
