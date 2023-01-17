// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle

@Service
@State(
    name = "KotlinNotebookOptionsProvider",
    presentableName = KotlinNotebookProjectOptionsProvider.PresentableNameGetter::class,
    storages = [Storage("kotlinNotebook.xml")]
)
class KotlinNotebookProjectOptionsProvider(
    private val project: Project
): PersistentStateComponent<KotlinNotebookProjectOptionsProvider.State>, Disposable {

    companion object {
        fun getInstance(project: Project): KotlinNotebookProjectOptionsProvider = project.service()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(newState: State) {
        state = newState
    }


    data class State(
        var jdk: KotlinNotebookJdkOption = ProjectJdkOption,
        var shouldBuildProject: Boolean = true,
        var shouldLimitTypeHintsByActiveCell: Boolean = false,
    )

    class PresentableNameGetter: com.intellij.openapi.components.State.NameGetter() {
        override fun get(): String = JupyterKotlinBundle.message("kotlin.jupyter.settings.title")
    }

    override fun dispose() {
    }
}