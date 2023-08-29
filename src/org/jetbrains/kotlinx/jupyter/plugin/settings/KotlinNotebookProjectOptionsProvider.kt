// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import java.util.*

@Service(Service.Level.PROJECT)
@State(
    name = "KotlinNotebookOptionsProvider",
    presentableName = KotlinNotebookProjectOptionsProvider.PresentableNameGetter::class,
    storages = [Storage("kotlinNotebook.xml")],
    category = SettingsCategory.PLUGINS
)
class KotlinNotebookProjectOptionsProvider :
    DelegatingOptionsProvider<KotlinNotebookProjectOptionsProvider.State, KotlinNotebookProjectOptionsProvider.Listener>(
        State(),
        Listener::class.java
    )
{
    var kernelVersion: String by propNarrowing(
        State::kernelVersion, internalToExternal = { it.orEmpty() }
    ).onChange(Listener::onKernelVersionChanged)

    val jdk get() = KotlinNotebookJdkOption.fromName(jdkName)
    internal var jdkName: String? by prop(
        State::jdkName
    ).onChange(Listener::onJdkChanged)

    var jvmTargetForSnippets: LanguageLevel? by prop(
        State::jvmTargetForSnippets,
        { it?.let { LanguageLevel.parse(it) } },
        { it?.toJavaVersion()?.toFeatureString() },
    ).onChange(Listener::onJvmTargetForSnippetsChanged)

    var heapMaxLimitInMib by prop(State::heapMaxLimitInMib)
        internal set
    var extraJvmArguments by prop(State::extraJvmArguments)
        internal set
    var extraEnvironmentVariables by prop(State::extraEnvironmentVariables)
        internal set

    var shouldLimitTypeHintsByActiveCell by prop(State::shouldLimitTypeHintsByActiveCell)
        internal set
    var shouldBuildProject by prop(State::shouldBuildProject)
        internal set
    var shouldAddProjectLibrariesToClasspath by prop(State::shouldAddProjectLibrariesToClasspath)
        internal set

    @RequiresEdt
    internal fun getNewKotlinNotebookSettings(): KotlinNotebookSettings {
        return KotlinNotebookSettings(
            if (shouldBuildProject) KotlinNotebookDependencies.All else KotlinNotebookDependencies.None,
            if (shouldAddProjectLibrariesToClasspath) KotlinNotebookDependencies.All else KotlinNotebookDependencies.None
        )
    }

    class State : BaseState() {
        var kernelVersion by string(currentKernelVersion.toMavenVersion())
        var jdkName by string(null)
        var jvmTargetForSnippets by string(null)
        var heapMaxLimitInMib by property(DEFAULT_HEAP_MAX_LIMIT_MIB)
        var extraJvmArguments by list<String>()
        var extraEnvironmentVariables by linkedMap<String, String>()
        var shouldLimitTypeHintsByActiveCell by property(false)

        // default settings for new notebooks
        var shouldBuildProject by property(false)
        var shouldAddProjectLibrariesToClasspath by property(true)
    }

    class PresentableNameGetter : com.intellij.openapi.components.State.NameGetter() {
        override fun get(): String = KotlinNotebookBundle.message("kotlin.jupyter.settings.title")
    }

    interface Listener : EventListener {
        fun onJdkChanged() {}
        fun onJvmTargetForSnippetsChanged() {}
        fun onKernelVersionChanged() {}
    }


    companion object {
        fun getInstance(project: Project): KotlinNotebookProjectOptionsProvider = project.service()

        const val DEFAULT_HEAP_MAX_LIMIT_MIB = 3256
    }
}
