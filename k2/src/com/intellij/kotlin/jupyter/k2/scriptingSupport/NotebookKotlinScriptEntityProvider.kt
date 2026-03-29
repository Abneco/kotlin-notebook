// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport

import com.intellij.kotlin.jupyter.core.util.getTopLevelFileOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider.Companion.findKotlinScriptEntity

/**
 * Notebook-specific lookup: resolves cell virtual files to the top-level `.ipynb` file
 * before looking up the workspace model entity.
 */
class NotebookKotlinScriptEntityProvider : KotlinScriptEntityProvider {
    override fun provide(project: Project, virtualFile: VirtualFile): KotlinScriptEntity? =
        virtualFile.getTopLevelFileOrNull()?.let { findKotlinScriptEntity(project, it) }
}
