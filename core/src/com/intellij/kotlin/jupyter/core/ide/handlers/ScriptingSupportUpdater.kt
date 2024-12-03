// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.ide.handlers

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project


data class UpdaterConstructorData(
    val project: Project,
    val parentDisposable: Disposable
)

/**
 * Updater is required to handle scripting dependencies update for either of Kotlin modes.
 * [Factory] is used to find implementation in each of modules (k1 or k2).
 */
interface ScriptingSupportUpdater : KotlinPluginModeAwareHandler {
    fun updateScripts()

    fun interface Factory {
        fun create(updaterConstructor: UpdaterConstructorData): ScriptingSupportUpdater
    }

    companion object {
        private val EP: ExtensionPointName<Factory> = ExtensionPointName.create("com.intellij.kotlin.jupyter.core.scriptingSupportUpdaterFactory")

        fun create(project: Project, parentDisposable: Disposable): ScriptingSupportUpdater {
            val configuration = UpdaterConstructorData(project, parentDisposable)
            return EP.extensionList.first().create(configuration)
        }
    }
}

