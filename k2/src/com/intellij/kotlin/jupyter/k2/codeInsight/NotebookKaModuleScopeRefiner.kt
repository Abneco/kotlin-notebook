// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.codeInsight

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.kotlin.jupyter.k2.scriptingSupport.KotlinNotebookScriptEntitySource
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinContentScopeRefiner
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules.KaScriptDependencyLibraryModuleImpl
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.library.KaEntityBasedLibraryModuleBase
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.librarySource.KaLibrarySourceModuleBase
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory

/**
 * The purpose of this class is to provide additional [com.intellij.psi.search.SearchScope] for the [KaModule]
 * without explicitly passing project roots to the libraries.
 * This way, go-to declaration would work for the project symbols.
 */
class NotebookKaModuleScopeRefiner : KotlinContentScopeRefiner {
    override fun getEnlargementScopes(module: KaModule): List<GlobalSearchScope> {
        val scriptSourceLibrary = module as? KaLibrarySourceModuleBase ?: return emptyList()
        val binaryLibrary = scriptSourceLibrary.binaryLibrary as? KaScriptDependencyLibraryModuleImpl ?: return emptyList()
        if (binaryLibrary.entity.entitySource !is KotlinNotebookScriptEntitySource) return emptyList()

        val project = module.project
        val requiresPatching = binaryLibrary.isSearchScopePatchingRequired()
        if (!requiresPatching) return emptyList()

        return buildList {
            add(
                GlobalSearchScope.getScopeRestrictedByFileTypes(
                    ProjectScope.getContentScope(project),
                    KotlinFileType.INSTANCE,
                    JavaFileType.INSTANCE
                )
            )
        }
    }

    /**
     * Determines whatever this particular library contains binary roots that correspond to the project files.
     */
    private fun KaEntityBasedLibraryModuleBase<KotlinScriptLibraryEntity, *>.isSearchScopePatchingRequired(): Boolean {
        val projectPrefix = project.guessProjectDir()?.path ?: project.basePath ?: "/${project.name}/"
        return binaryRoots.any { root ->
            root.isDirectory()
                    && root.invariantSeparatorsPathString.contains(projectPrefix)
        }
    }
}