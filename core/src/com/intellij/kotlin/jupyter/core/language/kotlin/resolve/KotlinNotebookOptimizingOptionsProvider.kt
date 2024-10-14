// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.language.kotlin.resolve

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptModuleInfo
import org.jetbrains.kotlin.idea.caches.resolve.ResolveOptimizingOptionsProvider
import org.jetbrains.kotlin.resolve.scopes.optimization.OptimizingOptions

class KotlinNotebookOptimizingOptionsProvider: ResolveOptimizingOptionsProvider {
    override fun getOptimizingOptions(
        project: Project,
        descriptor: ModuleDescriptor,
        moduleInfo: IdeaModuleInfo
    ): OptimizingOptions? {
        if (moduleInfo !is ScriptModuleInfo) return null

        val scriptFile = moduleInfo.scriptFile
        if (scriptFile !is VirtualFileWindow) return null

        val service = JupyterCompilerService.getInstance(project)
        if (!scriptFile.name.endsWith(service.fileSuffix)) return null

        return KotlinNotebookOptimizingOptions
    }

    private object KotlinNotebookOptimizingOptions: OptimizingOptions {
        override fun shouldCalculateAllNamesForLazyImportScopeOptimizing(moduleDescriptor: ModuleDescriptor?): Boolean {
            return false
        }
    }
}
