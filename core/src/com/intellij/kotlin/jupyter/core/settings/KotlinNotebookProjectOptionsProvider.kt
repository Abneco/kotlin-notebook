// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
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
    ), Disposable
{
    var kernelVersion: String by propNarrowing(
        State::kernelVersion, internalToExternal = { it.orEmpty() }
    ).onChange(Listener::onKernelVersionChanged)

    var ignoreOutdatedKernelVersion: Boolean by prop(State::ignoreOutdatedKernelVersion)
        internal set

    val jdk: KotlinNotebookJdkOption get() = KotlinNotebookJdkOption.fromName(jdkName)
    internal var jdkName: String? by prop(
        State::jdkName
    ).onChange(Listener::onJdkChanged)

    var jvmTargetForSnippets: LanguageLevel? by prop(
        State::jvmTargetForSnippets,
        { it?.let { LanguageLevel.parse(it) } },
        { it?.toCanonicalString() },
    ).onChange(Listener::onJvmTargetForSnippetsChanged)

    var heapMaxLimitInMib: Int by prop(State::heapMaxLimitInMib)
        internal set
    var extraJvmArguments: MutableList<String> by prop(State::extraJvmArguments)
        internal set
    var extraCompilerArguments: MutableList<String> by prop(
        State::extraCompilerArguments
    ).onChange(Listener::onExtraCompilerArgumentsChanged)
        internal set
    var extraEnvironmentVariables: MutableMap<String, String> by prop(State::extraEnvironmentVariables)
        internal set

    var shouldLimitTypeHintsByActiveCell: Boolean by prop(State::shouldLimitTypeHintsByActiveCell)
        internal set
    var shouldAddProjectLibrariesToClasspath: Boolean by prop(State::shouldAddProjectLibrariesToClasspath)
        internal set
    var shouldShowNotebookVariables: Boolean by prop(State::shouldShowNotebookVariables)
        internal set
    var shouldFocusOnVariables: Boolean by prop(State::shouldFocusOnNotebookVariables)
        internal set

    class State : BaseState() {
        var kernelVersion: String? by string(currentKernelVersion.toMavenVersion())
        var ignoreOutdatedKernelVersion: Boolean by property(false)
        var jdkName: String? by string(null)
        var jvmTargetForSnippets: String? by string(null)
        var heapMaxLimitInMib: Int by property(DEFAULT_HEAP_MAX_LIMIT_MIB)
        var extraJvmArguments: MutableList<String> by list()
        var extraCompilerArguments: MutableList<String> by list()
        var extraEnvironmentVariables: MutableMap<String, String> by linkedMap()

        var shouldLimitTypeHintsByActiveCell: Boolean by property(false)

        // default settings for new notebooks
        var shouldAddProjectLibrariesToClasspath: Boolean by property(
            defaultValue = KotlinNotebookDependenciesProperty.defaultValue == KotlinNotebookDependencies.AllLibraries
        )
        var shouldShowNotebookVariables: Boolean by property(false)
        var shouldFocusOnNotebookVariables: Boolean by property(false)
    }

    override fun dispose() {}

    class PresentableNameGetter : com.intellij.openapi.components.State.NameGetter() {
        override fun get(): String = KotlinNotebookBundle.message("kotlin.jupyter.settings.title")
    }

    interface Listener : EventListener {
        fun onJdkChanged() {}
        fun onJvmTargetForSnippetsChanged() {}
        fun onKernelVersionChanged() {}
        fun onExtraCompilerArgumentsChanged() {}
    }


    companion object {
        fun getInstance(project: Project): KotlinNotebookProjectOptionsProvider = project.service()

        private const val DEFAULT_HEAP_MAX_LIMIT_MIB: Int = 3256
    }
}
