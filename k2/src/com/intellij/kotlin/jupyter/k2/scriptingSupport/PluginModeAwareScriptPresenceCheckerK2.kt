// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.PluginModeAwareScriptPresenceChecker
import com.intellij.kotlin.jupyter.core.scriptingSupport.workSpaceSnapshot
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.entities
import org.jetbrains.kotlin.idea.core.script.scriptConfigurationsSourceOfType

private class PluginModeAwareScriptPresenceCheckerFactoryK2 : PluginModeAwareScriptPresenceChecker.Factory {
    override fun create(project: Project): PluginModeAwareScriptPresenceChecker {
        return PluginModeAwareScriptPresenceCheckerK2(project)
    }
}

private class PluginModeAwareScriptPresenceCheckerK2(
    private val project: Project
): PluginModeAwareScriptPresenceChecker {
    private fun checkSourceIsNotEmpty(notebookFile: BackedNotebookVirtualFile): Boolean {
        val scriptConfigurationsSource = project.scriptConfigurationsSourceOfType<NotebookScriptConfigurationsSource>()?.data?.get()
        if (scriptConfigurationsSource == null) {
            return false
        }

        return scriptConfigurationsSource.getConfigurationsForNotebook(notebookFile.file)?.isNotEmpty() == true
    }

    override fun checkPresentInCache(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: String): Boolean {
        val cache = project.workSpaceSnapshot

        return checkSourceIsNotEmpty(virtualFile) && cache.entities<LibraryEntity>()
            .filter {
                it.roots.any { root -> root.url.url.contains(lastCompiledScriptPath) }
            }.iterator().hasNext()
    }
}