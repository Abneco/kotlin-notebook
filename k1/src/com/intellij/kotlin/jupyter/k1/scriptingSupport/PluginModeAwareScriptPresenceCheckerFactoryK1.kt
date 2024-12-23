// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.scriptingSupport

import com.intellij.kotlin.jupyter.core.scriptingSupport.PluginModeAwareScriptPresenceChecker
import com.intellij.kotlin.jupyter.core.scriptingSupport.ScriptCheckerConfiguration
import com.intellij.kotlin.jupyter.core.scriptingSupport.scriptConfigurationsClassCache

private class PluginModeAwareScriptPresenceCheckerFactoryK1 : PluginModeAwareScriptPresenceChecker.Factory {
    override fun create(configuration: ScriptCheckerConfiguration): PluginModeAwareScriptPresenceChecker {
        val project = configuration.project

        return PluginModeAwareScriptPresenceChecker { lastCompiledScriptPath: String ->
          val cache = project.scriptConfigurationsClassCache

          cache.allDependenciesClassFiles.any {
            it.presentableUrl == lastCompiledScriptPath
          }
        }
    }
}