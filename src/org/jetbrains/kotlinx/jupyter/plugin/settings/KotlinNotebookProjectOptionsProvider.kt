// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle

@Service(Service.Level.PROJECT)
@State(
    name = "KotlinNotebookOptionsProvider",
    presentableName = KotlinNotebookProjectOptionsProvider.PresentableNameGetter::class,
    storages = [Storage("kotlinNotebook.xml")]
)
class KotlinNotebookProjectOptionsProvider : SimplePersistentStateComponent<KotlinNotebookProjectOptionsProvider.State>(State()) {

    companion object {
        fun getInstance(project: Project): KotlinNotebookProjectOptionsProvider = project.service()

        const val DEFAULT_HEAP_MAX_LIMIT_MIB = 3256
    }

    class State : BaseState() {
        var jdkPath by string(null)
        var jdkName by string(null)
        var heapMaxLimitInMib by property(DEFAULT_HEAP_MAX_LIMIT_MIB)
        var extraJvmArguments by list<String>()
        var shouldBuildProject by property(false)
        var shouldLimitTypeHintsByActiveCell by property(false)
        var shouldAddProjectLibrariesToClasspath by property(false)

        val jdk: KotlinNotebookJdkOption
            get() {
                migrateJdkPath()
                return KotlinNotebookJdkOption.fromName(jdkName)
            }

        internal fun migrateJdkPath() {
            if (jdkName == null && jdkPath != null) {
                // migration from jdkPath to jdkName
                val jdk = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()).firstOrNull {
                    it.homePath == jdkPath
                }
                jdkName = jdk?.name
                jdkPath = null
            }
        }
    }

    class PresentableNameGetter : com.intellij.openapi.components.State.NameGetter() {
        override fun get(): String = JupyterKotlinBundle.message("kotlin.jupyter.settings.title")
    }
}