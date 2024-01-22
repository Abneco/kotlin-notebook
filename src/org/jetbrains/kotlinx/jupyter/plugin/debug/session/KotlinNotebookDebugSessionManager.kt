// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.debug.session

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.util.NotebookProjectLevelService
import org.jetbrains.kotlinx.jupyter.plugin.util.findNotebookVirtualFileOrNull
import org.jetbrains.kotlinx.jupyter.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.startup.create
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class KotlinNotebookDebugSessionManager(
    private val project: Project,
    coroutineScope: CoroutineScope
) : NotebookProjectLevelService<KotlinNotebookDebugSession>(coroutineScope) {
    private val portsGenerator = PortsGenerator.create(8000, 65535)

    private val nextTargetDebugPortOrNull: Int?
        get() {
            val isKeepOpened = KotlinNotebookProjectOptionsProvider.getInstance(project).shouldOpenDebugPort
            return if (isKeepOpened) portsGenerator.randomPort() else null
        }

    fun getByPath(path: Path): KotlinNotebookDebugSession? {
        return mapping.firstNotNullOfOrNull {
            if (it.key.path == path.toString()) it.value else null
        } ?: run {
            val backedNotebookVirtualFile = path.findNotebookVirtualFileOrNull() ?: return null
            getOrCreate(backedNotebookVirtualFile)
        }
    }

    override fun createInstance(virtualFile: BackedNotebookVirtualFile): KotlinNotebookDebugSession {
        return KotlinNotebookDebugSession(
            virtualFile,
            project,
            this,
            coroutineScope
        ) { nextTargetDebugPortOrNull }
    }

    companion object {
        fun getInstance(project: Project) = project.service<KotlinNotebookDebugSessionManager>()

        fun getForFile(project: Project, virtualFile: BackedNotebookVirtualFile): KotlinNotebookDebugSession {
            return getInstance(project).getOrCreate(virtualFile)
        }
    }
}