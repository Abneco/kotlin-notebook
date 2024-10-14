// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.session

import com.intellij.debugger.engine.DebugProcess
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.debug.util.connection.DebugConnectionUtility
import com.intellij.kotlin.jupyter.core.settings.isKernelVersionEnoughForInstrumentation
import com.intellij.kotlin.jupyter.core.util.NotebookProjectLevelService
import com.intellij.kotlin.jupyter.core.util.findNotebookVirtualFileOrNull
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class KotlinNotebookDebugSessionManager(
    private val project: Project,
    coroutineScope: CoroutineScope
) : NotebookProjectLevelService<KotlinNotebookDebugSession>(coroutineScope) {
    private val portsGenerator = DebugConnectionUtility.debugPortsGenerator

    private val nextTargetDebugPortOrNull: Int?
        get() {
            val isSuitable = project.isKernelVersionEnoughForInstrumentation
            return if (isSuitable && !ApplicationManager.getApplication().isUnitTestMode)
                portsGenerator.randomPort()
            else null
        }

    fun getByDebugProcessOrNull(debugProcess: DebugProcess?): KotlinNotebookDebugSession? {
        if (debugProcess == null) return null
        return mapping.firstNotNullOfOrNull {
            if (it.value.debuggerSession?.process == debugProcess) it.value else null
        }
    }

    fun getByPath(path: Path): KotlinNotebookDebugSession? {
        return mapping.firstNotNullOfOrNull {
            if (it.key.path == path.toString()) it.value else null
        } ?: run {
            val backedNotebookVirtualFile = path.findNotebookVirtualFileOrNull() ?: return null
            getOrCreate(backedNotebookVirtualFile)
        }
    }

    override fun createInstance(virtualFile: BackedNotebookVirtualFile, fileScope: CoroutineScope): KotlinNotebookDebugSession {
        return KotlinNotebookDebugSession(
          virtualFile,
          project,
          this,
          fileScope
        ) { nextTargetDebugPortOrNull }
    }

    companion object {
        fun getInstance(project: Project) = project.service<KotlinNotebookDebugSessionManager>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): KotlinNotebookDebugSession {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}