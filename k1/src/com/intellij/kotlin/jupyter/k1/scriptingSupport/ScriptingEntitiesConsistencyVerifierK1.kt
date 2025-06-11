// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.projectModel.kotlin.getIndexedTopLevelClassifiers
import com.intellij.kotlin.jupyter.core.scriptingSupport.ScriptingEntitiesConsistencyVerifier
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.psi.search.ProjectAndLibrariesScope
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.k1.ucache.ScriptClassRootsCache
import kotlin.script.experimental.api.KotlinType
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
    override fun isScriptPathConsistentWithModel(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: VirtualFileUrl): Boolean {
        val cache = project.scriptConfigurationsClassCache
        val urlManager = project.workspaceModel.getVirtualFileUrlManager()
        val presentableUrl = lastCompiledScriptPath.presentableUrl

        return cache.allDependenciesClassFiles.any {
            it.toVirtualFileUrl(urlManager).presentableUrl == presentableUrl
        }
    }

    override suspend fun filterTypesPresentInIndexes(
        virtualFile: BackedNotebookVirtualFile,
        types: Collection<KotlinType>
    ): Collection<KotlinType> {
        val indexedNames = smartReadAction(project) {
            val cache = project.scriptConfigurationsClassCache
            val cachedForScript = cache.scripts.firstNotNullOfOrNull { (scriptPath, cacheInfo) ->
                val fromNotebook = scriptPath.contains(virtualFile.file.nameWithoutExtension)
                if (fromNotebook) {
                    cacheInfo
                } else null
            }?.heavyCache?.get()

            val projectScope = cachedForScript?.classFilesScope?.uniteWith(
                ProjectAndLibrariesScope(project)
            )

            projectScope?.getIndexedTopLevelClassifiers(project)
        }

        if (indexedNames == null) {
            return emptyList()
        }

        return readAction {
            types.filter {
                it.typeName in indexedNames.map {
                    indexedNames -> indexedNames.fqName?.asString()
                }
            }
        }
    }

    // Do not have separation for K1
    override fun isScriptFileConfigurationConsistentWithModel(
        virtualFile: BackedNotebookVirtualFile,
        compilationConfiguration: ScriptCompilationConfiguration
    ): Boolean = true

    private val Project.scriptConfigurationsClassCache: ScriptClassRootsCache
        get() = ScriptConfigurationManager.getInstance(this).updater.classpathRoots
}
