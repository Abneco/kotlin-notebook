// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import java.util.EventListener
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty

@Service(Service.Level.PROJECT)
@State(
    name = "KotlinNotebookOptionsProvider",
    presentableName = KotlinNotebookProjectOptionsProvider.PresentableNameGetter::class,
    storages = [Storage("kotlinNotebook.xml")],
    category = SettingsCategory.PLUGINS
)
class KotlinNotebookProjectOptionsProvider : SimplePersistentStateComponent<KotlinNotebookProjectOptionsProvider.State>(State()) {
    private val eventDispatcher = EventDispatcher.create(Listener::class.java)

    var kernelVersion: String by PropertyDelegate(
        State::kernelVersion,
        { it.orEmpty() },
        { it },
        Listener::onKernelVersionChanged
    )

    val jdk get() = KotlinNotebookJdkOption.fromName(jdkName)
    internal var jdkName: String? by PropertyDelegate(
        State::jdkName,
        { it },
        { it },
        Listener::onJdkChanged
    )

    var jvmTargetForSnippets: LanguageLevel? by PropertyDelegate(
        State::jvmTargetForSnippets,
        { it?.let { LanguageLevel.parse(it) } },
        { it?.toJavaVersion()?.toFeatureString() },
        Listener::onJvmTargetForSnippetsChanged
    )

    var heapMaxLimitInMib by stateProp(State::heapMaxLimitInMib)
        internal set
    var extraJvmArguments by stateProp(State::extraJvmArguments)
        internal set
    var extraEnvironmentVariables by stateProp(State::extraEnvironmentVariables)
        internal set

    var shouldLimitTypeHintsByActiveCell by stateProp(State::shouldLimitTypeHintsByActiveCell)
        internal set
    var shouldBuildProject by stateProp(State::shouldBuildProject)
        internal set
    var shouldAddProjectLibrariesToClasspath by stateProp(State::shouldAddProjectLibrariesToClasspath)
        internal set

    fun addListener(listener: Listener, disposable: Disposable) {
        eventDispatcher.addListener(listener, disposable)
    }

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

    private inner class PropertyDelegate<InternalT, ExternalT>(
        private val stateProperty: KMutableProperty1<State, InternalT>,
        private val internalToExternal: (InternalT) -> ExternalT,
        private val externalToInternal: (ExternalT) -> InternalT,
        private val onChange: Listener.() -> Unit = {},
    ) {
        operator fun getValue(thisRef: KotlinNotebookProjectOptionsProvider, property: KProperty<*>): ExternalT {
            return internalToExternal(stateProperty.get(state))
        }

        operator fun setValue(thisRef: KotlinNotebookProjectOptionsProvider, property: KProperty<*>, value: ExternalT) {
            val oldValue = getValue(thisRef, property)
            stateProperty.set(state, externalToInternal(value))
            if (oldValue != value) {
                eventDispatcher.multicaster.onChange()
            }
        }
    }

    private fun <V> stateProp(property: KMutableProperty1<State, V>) = PropertyDelegate(property, { it }, { it })

    companion object {
        fun getInstance(project: Project): KotlinNotebookProjectOptionsProvider = project.service()

        const val DEFAULT_HEAP_MAX_LIMIT_MIB = 3256
    }
}
