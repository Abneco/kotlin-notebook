// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.kotlin.jupyter.core.scriptingSupport.PluginModeAwareScriptPresenceChecker
import com.intellij.kotlin.jupyter.core.scriptingSupport.ScriptCheckerConfiguration
import com.intellij.kotlin.jupyter.core.scriptingSupport.workSpaceSnapshot
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.entities
import org.jetbrains.kotlin.idea.core.script.scriptConfigurationsSourceOfType

private class PluginModeAwareScriptPresenceCheckerFactoryK2: PluginModeAwareScriptPresenceChecker.Factory {
    override fun create(configuration: ScriptCheckerConfiguration): PluginModeAwareScriptPresenceChecker {
        val (project, notebookFile) = configuration

        return PluginModeAwareScriptPresenceChecker { lastCompiledScriptPath: String ->
          fun checkSourceIsNotEmpty(): Boolean {
            val scriptConfigurationsSource = project.scriptConfigurationsSourceOfType<NotebookScriptConfigurationsSource>()?.data?.get()
                                             ?: return false
            return scriptConfigurationsSource.getConfigurationsForNotebook(notebookFile.file)?.isNotEmpty() == true
          }

          val cache = project.workSpaceSnapshot

          checkSourceIsNotEmpty() && cache.entities<LibraryEntity>()
            .filter {
              it.roots.any { root -> root.url.url.contains(lastCompiledScriptPath) }
            }.iterator().hasNext()
        }
    }
}