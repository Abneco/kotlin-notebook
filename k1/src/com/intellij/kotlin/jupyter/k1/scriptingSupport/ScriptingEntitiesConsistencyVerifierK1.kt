// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.scriptingSupport

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.projectModel.kotlin.getIndexedTopLevelClassifiers
import com.intellij.kotlin.jupyter.core.scriptingSupport.ScriptingEntitiesConsistencyVerifier
import com.intellij.kotlin.jupyter.core.scriptingSupport.scriptConfigurationsClassCache
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
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
    override fun isScriptPathConsistentWithModel(virtualFile: BackedNotebookVirtualFile, lastCompiledScriptPath: String): Boolean {
        val cache = project.scriptConfigurationsClassCache

        return cache.allDependenciesClassFiles.any {
            it.presentableUrl == lastCompiledScriptPath
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

            cachedForScript?.classFilesScope?.getIndexedTopLevelClassifiers(project)
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
}