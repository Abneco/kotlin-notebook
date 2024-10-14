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

    var ignoreOutdatedKernelVersion by prop(State::ignoreOutdatedKernelVersion)
        internal set

    val jdk get() = KotlinNotebookJdkOption.fromName(jdkName)
    internal var jdkName: String? by prop(
        State::jdkName
    ).onChange(Listener::onJdkChanged)

    var jvmTargetForSnippets: LanguageLevel? by prop(
        State::jvmTargetForSnippets,
        { it?.let { LanguageLevel.parse(it) } },
        { it?.toCanonicalString() },
    ).onChange(Listener::onJvmTargetForSnippetsChanged)

    var heapMaxLimitInMib by prop(State::heapMaxLimitInMib)
        internal set
    var extraJvmArguments by prop(State::extraJvmArguments)
        internal set
    var extraEnvironmentVariables by prop(State::extraEnvironmentVariables)
        internal set

    var shouldLimitTypeHintsByActiveCell by prop(State::shouldLimitTypeHintsByActiveCell)
        internal set
    var shouldAddProjectLibrariesToClasspath by prop(State::shouldAddProjectLibrariesToClasspath)
        internal set
    var shouldShowNotebookVariables by prop(State::shouldShowNotebookVariables)
        internal set

    class State : BaseState() {
        var kernelVersion by string(currentKernelVersion.toMavenVersion())
        var ignoreOutdatedKernelVersion by property(false)
        var jdkName by string(null)
        var jvmTargetForSnippets by string(null)
        var heapMaxLimitInMib by property(DEFAULT_HEAP_MAX_LIMIT_MIB)
        var extraJvmArguments by list<String>()
        var extraEnvironmentVariables by linkedMap<String, String>()

        var shouldLimitTypeHintsByActiveCell by property(false)

        // default settings for new notebooks
        var shouldAddProjectLibrariesToClasspath by property(true)
        var shouldShowNotebookVariables by property(false)
    }

    override fun dispose() {}

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
