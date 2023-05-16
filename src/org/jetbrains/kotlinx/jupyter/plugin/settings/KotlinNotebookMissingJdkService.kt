// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications

@Service(Service.Level.PROJECT)
class KotlinNotebookMissingJdkService(private val project: Project) : Disposable {
    private val projectJdkListener = ProjectRootManagerEx.ProjectJdkListener { updateNotifications() }
    private val kotlinNotebookOptionsListener = KotlinNotebookProjectOptionsProvider.Listener { updateNotifications() }
    private val jdkTableListener = object : ProjectJdkTable.Listener {
        override fun jdkAdded(jdk: Sdk) = updateNotifications()
        override fun jdkRemoved(jdk: Sdk) = updateNotifications()
    }

    init {
        ProjectRootManagerEx.getInstanceEx(project).addProjectJdkListener(projectJdkListener)
        KotlinNotebookProjectOptionsProvider.getInstance(project).addListener(kotlinNotebookOptionsListener, this)
        project.messageBus.connect(this).subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, jdkTableListener)
    }

    private fun updateNotifications() {
        val extension = EditorNotificationProvider.EP_NAME.findExtension(KotlinNotebookMissingJdkEditorNotification::class.java, project)
                        ?: return
        EditorNotifications.getInstance(project).updateNotifications(extension)
    }

    override fun dispose() {
        ProjectRootManagerEx.getInstanceEx(project).removeProjectJdkListener(projectJdkListener)
    }
}