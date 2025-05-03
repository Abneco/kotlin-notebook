// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.projectModel

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.scriptingSupport.workSpaceSnapshot
import com.intellij.kotlin.jupyter.k2.scriptingSupport.KotlinNotebookScriptEntitySource
import com.intellij.kotlin.jupyter.k2.scriptingSupport.toK2RuntimeModuleName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap

fun BackedNotebookVirtualFile.findK2WorkspaceModule(project: Project): Module? {
    val moduleName = file.toK2RuntimeModuleName(project)
    val workSpaceSnapshot = project.workSpaceSnapshot

    val entity = workSpaceSnapshot.entitiesBySource {
        it is KotlinNotebookScriptEntitySource
    }.firstOrNull {
        it is ModuleEntity && it.name == moduleName
    } as ModuleEntity?

    if (entity == null) {
        return null
    }

    return workSpaceSnapshot.moduleMap.getDataByEntity(entity) as? Module
}