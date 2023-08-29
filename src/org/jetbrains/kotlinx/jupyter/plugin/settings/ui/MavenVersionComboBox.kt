// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings.ui

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import org.jetbrains.kotlinx.jupyter.plugin.resources.ArtifactDescription
import org.jetbrains.kotlinx.jupyter.plugin.resources.toIntellijModelDescription
import javax.swing.ComboBoxModel
import kotlin.reflect.KMutableProperty0

private fun interface MavenVersionModelProvider {
    fun provideModel(versions: Collection<String>): ComboBoxModel<String>
}

abstract class MavenVersionComboBox : ComboBox<String>() {
    abstract var version: String
    abstract val isReady: Boolean
}

private class MavenVersionComboBoxImpl(
    private val project: Project,
    private val artifactDescription: ArtifactDescription,
    private val modelProvider: MavenVersionModelProvider,
) : MavenVersionComboBox() {
    private var state = State.NOT_LOADED

    override var version: String
        get() {
            return when (state) {
                State.NOT_LOADED -> ""
                State.LOADED -> (selectedItem as String?).orEmpty()
            }
        }
        set(@NlsSafe value) {
            if (state == State.LOADED) {
                selectedItem = value
            }
        }

    override val isReady get() = state == State.LOADED

    init {
        reloadVersionsAsync()
    }

    private fun reloadVersionsAsync() {
        val promise = JarRepositoryManager.getAvailableVersions(project, artifactDescription.toIntellijModelDescription())
        promise.onSuccess(::initializeComboBox)
    }

    private fun initializeComboBox(versions: Collection<String>) {
        setModel(modelProvider.provideModel(versions))
        state = State.LOADED
    }

    private enum class State {
        NOT_LOADED, LOADED
    }
}

private class MavenVersionModelProviderImpl(
    private val initialVersion: String,
    private val versionComparator: Comparator<String>,
) : MavenVersionModelProvider {
    override fun provideModel(versions: Collection<String>): ComboBoxModel<String> {
        val allVersions = buildSet {
            addAll(versions)
            add(initialVersion)
        }.sortedWith(versionComparator)

        return CollectionComboBoxModel<String>().apply {
            add(allVersions)
            selectedItem = initialVersion
        }
    }
}

fun Row.mavenVersionComboBox(
    project: Project,
    artifactDescription: ArtifactDescription,
    versionProperty: KMutableProperty0<String>,
    versionComparator: Comparator<String> = Comparator.naturalOrder(),
): Cell<MavenVersionComboBox> {
    val initialVersion = versionProperty.get()

    val comboBox = MavenVersionComboBoxImpl(project, artifactDescription, MavenVersionModelProviderImpl(initialVersion, versionComparator))
    return cell(comboBox)
        .onReset {
            comboBox.version = versionProperty.get()
        }.onIsModified {
            comboBox.isReady && versionProperty.get() != comboBox.version
        }.onApply {
            if (comboBox.isReady) {
                versionProperty.set(comboBox.version)
            }
        }
}
