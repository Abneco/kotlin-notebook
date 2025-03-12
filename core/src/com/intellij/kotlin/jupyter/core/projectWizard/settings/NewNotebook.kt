// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.projectWizard.settings

import com.intellij.ide.RecentProjectsManager
import com.intellij.kotlin.jupyter.core.jupyter.actions.NotebookMode
import com.intellij.kotlin.jupyter.core.language.NotebookTemplate
import com.intellij.kotlin.jupyter.core.projectWizard.KOTLIN_NOTEBOOK_SCRATCH_PREFIX
import com.intellij.kotlin.jupyter.core.projectWizard.KotlinNotebookRootTypeInstance
import com.intellij.kotlin.jupyter.core.projectWizard.findEnclosingProjectPathOrUseParent
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.APP_CONFIG_FILE
import com.intellij.kotlin.jupyter.core.settings.DelegatingOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.prop
import com.intellij.kotlin.jupyter.core.settings.propNarrowing
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Path
import java.util.*

interface NewNotebookOptions {
    val notebookName: String
    val template: NotebookTemplate
    val notebookDirectory: String
    val notebookMode: NotebookMode
}

fun NewNotebookOptions.getActualProjectPath(): Path {
    return when(notebookMode) {
        NotebookMode.STANDARD -> {
            val pathString = requireNotNull(notebookDirectory)
            val virtualDirectory = VfsUtil.createDirectories(pathString)
            val projectDirectory = findEnclosingProjectPathOrUseParent(virtualDirectory)
            projectDirectory.toNioPath()
        }
        NotebookMode.LIGHT -> {
            KotlinNotebookRootTypeInstance.rootPath
        }
    }
}

class NewNotebookMutableOptions : NewNotebookOptions {
    override var template: NotebookTemplate by options::template
    override var notebookName: String by options::notebookName
    override var notebookDirectory: String = RecentProjectsManager.getInstance().suggestNewProjectLocation()
    override var notebookMode: NotebookMode by options::notebookMode

    private val options get() = service<NewNotebookOptionsState>()
}

@Service(Service.Level.APP)
@State(
    name = "KotlinNotebookNewNotebookOptionsProvider",
    presentableName = NewNotebookOptionsState.PresentableNameGetter::class,
    storages = [Storage(APP_CONFIG_FILE)],
    category = SettingsCategory.PLUGINS
)
class NewNotebookOptionsState :
    DelegatingOptionsProvider<NewNotebookOptionsState.State, NewNotebookOptionsState.Listener>(
        State(), Listener::class.java
    ) {

    class PresentableNameGetter : State.NameGetter() {
        override fun get(): String = KotlinNotebookBundle.message("kotlin.notebook.settings.new.notebook.settings.title")
    }

    var notebookName: String by propNarrowing(State::notebookName) { it ?: KOTLIN_NOTEBOOK_SCRATCH_PREFIX }
    var template: NotebookTemplate by prop(State::template)
    var notebookMode: NotebookMode by prop(State::notebookMode)

    class State: BaseState() {
        var notebookName: String? by string(KOTLIN_NOTEBOOK_SCRATCH_PREFIX)
        var template: NotebookTemplate by enum(NotebookTemplate.EMPTY)
        var notebookMode: NotebookMode by enum(NotebookMode.LIGHT)
    }

    interface Listener : EventListener
}
