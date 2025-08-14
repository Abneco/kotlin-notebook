// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.fir

import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrNull
import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrSelf
import com.intellij.kotlin.jupyter.core.util.isInsideKotlinNotebook
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.psi.PsiFile
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules.KaScriptDependencyLibraryModuleImpl
import org.jetbrains.kotlin.base.fir.scripting.projectStructure.modules.KaScriptModuleBase
import org.jetbrains.kotlin.idea.base.fir.projectStructure.FirKaModuleFactory
import org.jetbrains.kotlin.idea.base.projectStructure.ideProjectStructureProvider
import org.jetbrains.kotlin.idea.base.projectStructure.toKaLibraryModule
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependencyAware
import org.jetbrains.kotlin.psi.KtFile

/**
 * This class allows customizing which module should be created for the scripts inside the notebook.
 * Note that this extension should be exclusive
 * with [org.jetbrains.kotlin.base.fir.scripting.projectStructure.FirKaScriptingModuleFactory].
 */
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

    override val sdkDependency: KaLibraryModule?
        get() = ScriptDependencyAware.getInstance(project).getScriptSdk(
            virtualFile.getTopLevelFileOrSelf()
        )?.toKaLibraryModule(project)

    override val directRegularDependencies: List<KaModule> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val notebookFileUrl = file.virtualFile.getTopLevelFileOrNull()
            ?.toVirtualFileUrl(virtualFileUrlManager)
        if (notebookFileUrl == null) {
            return@lazy emptyList()
        }

        buildList {
            val current = currentSnapshot
            val index = current.getVirtualFileUrlIndex()
            val entities = index.findEntitiesByUrl(notebookFileUrl)
                .distinct().filterIsInstance<KotlinScriptEntity>()
                .flatMap { it.dependencies }
                .mapNotNull { current.resolve(it) }

            addAll(entities.flatMap {
                project.ideProjectStructureProvider.getKaScriptLibraryModules(it)
            })

            addIfNotNull(sdkDependency)
        }
    }
}