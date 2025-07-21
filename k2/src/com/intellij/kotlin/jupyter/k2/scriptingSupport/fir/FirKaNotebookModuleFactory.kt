// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.fir

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.kotlin.jupyter.core.util.isInsideKotlinNotebook
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules.KaScriptDependencyLibraryModuleImpl
import org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules.KaScriptModuleBase
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.base.fir.projectStructure.FirKaModuleFactory
import org.jetbrains.kotlin.idea.base.projectStructure.ideProjectStructureProvider
import org.jetbrains.kotlin.psi.KtFile

internal class FirKaNotebookModuleFactory : FirKaModuleFactory {
    override fun createScriptLibraryModule(
        project: Project, entity: KotlinScriptLibraryEntity
    ): KaLibraryModule {
        return KaScriptDependencyLibraryModuleImpl(entity, project)
    }

    override fun createKaModuleByPsiFile(file: PsiFile): KaModule? {
        if (file !is KtFile || file.isCompiled) return null
        if (!file.isInsideKotlinNotebook) return null
        return KaNotebookScriptModuleImpl(file)
    }
}

private class KaNotebookScriptModuleImpl(
    project: Project,
    override val file: KtFile,
    override val virtualFile: VirtualFile
) : KaScriptModuleBase(project, file.virtualFile) {
    constructor(file: KtFile) : this(file.project, file, file.virtualFile)

    override val directRegularDependencies: List<KaModule> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val ipynbFileUrl =
            (file.virtualFile as? VirtualFileWindow)?.delegate?.toVirtualFileUrl(virtualFileUrlManager) ?: return@lazy emptyList()

        buildList {
            val current = currentSnapshot
            val index = current.getVirtualFileUrlIndex()
            val entities =
                index.findEntitiesByUrl(ipynbFileUrl).distinct().filterIsInstance<KotlinScriptEntity>().flatMap { it.dependencies }
                    .mapNotNull { current.resolve(it) }

            addAll(entities.flatMap {
                project.ideProjectStructureProvider.getKaScriptLibraryModules(it)
            })
        } + sdkDependencies
    }
}