// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.ui

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.kotlin.jupyter.core.resources.ArtifactDescription
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.resources.toIntellijModelDescription
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import javax.swing.ComboBoxModel
import javax.swing.JLabel
import javax.swing.ListCellRenderer
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
    private val remoteRepositories: List<RemoteRepositoryDescription>,
    private val modelProvider: MavenVersionModelProvider,
    private val listCellRendererProvider: (defaultRenderer: ListCellRenderer<in String>) -> ListCellRenderer<in String> = { it },
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
        val promise = JarRepositoryManager.getAvailableVersions(
            project,
            artifactDescription.toIntellijModelDescription(),
            remoteRepositories
        )
        promise.onSuccess(::initializeComboBox)
    }

    private fun initializeComboBox(versions: Collection<String>) {
        runInEdt(ModalityState.stateForComponent(this)) {
            setModel(modelProvider.provideModel(versions))
            setRenderer(listCellRendererProvider(renderer))
            updateUI()

            state = State.LOADED
            selectedItemChanged()
        }
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
    defaultVersion: String?,
    versionProperty: KMutableProperty0<String>,
    versionComparator: Comparator<String> = Comparator.naturalOrder(),
    remoteArtifactsRepositories: List<RemoteRepositoryDescription> = listOf(),
): Cell<MavenVersionComboBox> {
    val initialVersion = versionProperty.get()

    val comboBox = MavenVersionComboBoxImpl(
        project,
        artifactDescription,
        remoteArtifactsRepositories,
        MavenVersionModelProviderImpl(initialVersion, versionComparator),
        listCellRendererProvider = { defaultRenderer ->
            ListCellRenderer<String> { list, value, index, isSelected, cellHasFocus ->

                @Suppress("HardCodedStringLiteral")
                defaultRenderer
                    .getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    .also { itemComponent ->
                        if (value == defaultVersion && itemComponent is JLabel) {
                            itemComponent.text = KotlinNotebookBundle.message("kotlin.jupyter.settings.kernel.version.default", value)
                        }
                    }
            }
        }
    )
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
