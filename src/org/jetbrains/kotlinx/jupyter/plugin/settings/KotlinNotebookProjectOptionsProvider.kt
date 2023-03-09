// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle

@Service(Service.Level.PROJECT)
@State(
    name = "KotlinNotebookOptionsProvider",
    presentableName = KotlinNotebookProjectOptionsProvider.PresentableNameGetter::class,
    storages = [Storage("kotlinNotebook.xml")]
)
class KotlinNotebookProjectOptionsProvider : PersistentStateComponent<KotlinNotebookProjectOptionsProvider.State>, Disposable {

    companion object {
        fun getInstance(project: Project): KotlinNotebookProjectOptionsProvider = project.service()

        const val DEFAULT_HEAP_MAX_LIMIT_MIB = 3256
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(newState: State) {
        state = newState
    }


    data class State(
        var jdk: KotlinNotebookJdkOption = ProjectJdkOption,
        var heapMaxLimitInMib: Int = DEFAULT_HEAP_MAX_LIMIT_MIB,
        var extraJvmArguments: List<String> = emptyList(),
        var shouldBuildProject: Boolean = false,
        var shouldLimitTypeHintsByActiveCell: Boolean = false,
        var shouldAddProjectLibrariesToClasspath: Boolean = false,
        var shouldShowExecutionCount: Boolean = true,
    ) {
        fun equals(other: State, project: Project): Boolean {
            return jdk.getPath(project) == other.jdk.getPath(project) &&
                    heapMaxLimitInMib == other.heapMaxLimitInMib &&
                    extraJvmArguments == other.extraJvmArguments &&
                    shouldBuildProject == other.shouldBuildProject &&
                    shouldLimitTypeHintsByActiveCell == other.shouldLimitTypeHintsByActiveCell &&
                    shouldAddProjectLibrariesToClasspath == other.shouldAddProjectLibrariesToClasspath &&
                    shouldShowExecutionCount == other.shouldShowExecutionCount
        }
    }

    class PresentableNameGetter: com.intellij.openapi.components.State.NameGetter() {
        override fun get(): String = JupyterKotlinBundle.message("kotlin.jupyter.settings.title")
    }

    override fun dispose() {
    }
}