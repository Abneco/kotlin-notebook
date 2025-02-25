// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.ide.handlers

import com.intellij.kotlin.jupyter.core.ide.handlers.ScriptingSupportUpdater.Companion.create
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project


data class UpdaterConstructorData(
    val project: Project,
    val parentDisposable: Disposable
)

/**
 * Updater is required to handle scripting dependencies update for either of Kotlin modes.
 * A particular service instance is used to find implementation in each of the modules (k1 or k2).
 *
 * [create] calls a [Factory] service for each of K1/K2 modes.
 */
interface ScriptingSupportUpdater : KotlinPluginModeAwareHandler {
    /**
     * Main logic for executing scripting update is contained here
     */
    fun updateScripts()

    fun interface Factory {
        fun create(updaterConstructor: UpdaterConstructorData): ScriptingSupportUpdater
    }

    companion object {
        fun create(project: Project, parentDisposable: Disposable): ScriptingSupportUpdater {
            val configuration = UpdaterConstructorData(project, parentDisposable)
            return project.service<Factory>().create(configuration)
        }
    }
}

