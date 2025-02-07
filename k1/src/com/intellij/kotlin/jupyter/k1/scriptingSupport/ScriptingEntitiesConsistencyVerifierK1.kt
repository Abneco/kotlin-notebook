// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.ScriptingEntitiesConsistencyVerifier
import com.intellij.kotlin.jupyter.core.scriptingSupport.scriptConfigurationsClassCache
import com.intellij.openapi.project.Project
import kotlin.script.experimental.api.ScriptCompilationConfiguration


private class ScriptingEntitiesConsistencyVerifierFactoryK1(
): ScriptingEntitiesConsistencyVerifier.Factory {
    override fun create(project: Project): ScriptingEntitiesConsistencyVerifier {
        return ScriptingEntitiesConsistencyVerifierK1(project)
    }
}

private class ScriptingEntitiesConsistencyVerifierK1(
    private val project: Project
) : ScriptingEntitiesConsistencyVerifier {
    override fun isScriptPathConsistentWithModel(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: String): Boolean {
        val cache = project.scriptConfigurationsClassCache

        return cache.allDependenciesClassFiles.any {
            it.presentableUrl == lastCompiledScriptPath
        }
    }

    // Do not have separation for K1
    override fun isScriptFileConfigurationConsistentWithModel(
        virtualFile: BackedNotebookVirtualFile,
        compilationConfiguration: ScriptCompilationConfiguration
    ): Boolean = true
}