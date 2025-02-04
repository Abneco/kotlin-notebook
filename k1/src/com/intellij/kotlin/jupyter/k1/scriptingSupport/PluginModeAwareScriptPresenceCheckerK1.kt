// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.PluginModeAwareScriptPresenceChecker
import com.intellij.kotlin.jupyter.core.scriptingSupport.scriptConfigurationsClassCache
import com.intellij.openapi.project.Project


private class PluginModeAwareScriptPresenceCheckerFactoryK1(
): PluginModeAwareScriptPresenceChecker.Factory {
    override fun create(project: Project): PluginModeAwareScriptPresenceChecker {
        return PluginModeAwareScriptPresenceCheckerK1(project)
    }
}

private class PluginModeAwareScriptPresenceCheckerK1(
    private val project: Project
) : PluginModeAwareScriptPresenceChecker {
    override fun checkPresentInCache(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: String): Boolean {
        val cache = project.scriptConfigurationsClassCache

        return cache.allDependenciesClassFiles.any {
            it.presentableUrl == lastCompiledScriptPath
        }
    }
}