// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.scriptingSupport

import com.intellij.kotlin.jupyter.core.ide.handlers.ScriptingSupportUpdater
import com.intellij.kotlin.jupyter.core.ide.handlers.UpdaterConstructorData
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager

class ScriptingSupportUpdaterFactoryK1: ScriptingSupportUpdater.Factory {
    override fun create(updaterConstructor: UpdaterConstructorData): ScriptingSupportUpdater {
        return K1ScriptingSupportUpdater(updaterConstructor.project)
    }
}

class K1ScriptingSupportUpdater(private val project: Project): ScriptingSupportUpdater {
    override fun updateScripts() {
        val updater = (ScriptConfigurationManager.getInstance(project) as CompositeScriptConfigurationManager).updater
        RecursionManager.doPreventingRecursion("${this::class}: update()", false) {
            updater.invalidateAndCommit()
        }
    }
}