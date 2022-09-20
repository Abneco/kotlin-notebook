// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.actions.refactor

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle

internal object RefactoringNotificationUtility {
    private fun prepareNotificationGroupTemplate() =
        NotificationGroupManager.getInstance().getNotificationGroup("Find Problems")

    private val informSingletonManager = SingletonNotificationManager("Find Problems", NotificationType.INFORMATION)

    private inline fun NotificationGroup.wrapActionInNotify(project: Project?, crossinline action: NotificationGroup.() -> Notification) =
        this.action().notify(project)

    fun showBytecodeRefactoringWarning(project: Project?) {
        prepareNotificationGroupTemplate().wrapActionInNotify(project) {
            createNotification(JupyterKotlinBundle.message("kotlin.jupyter.refactor.compiled.script"), NotificationType.WARNING)
            .setTitle(JupyterKotlinBundle.message("kotlin.jupyter.settings.title"))
        }
    }

    fun showExistingUsagesMessage(project: Project?, usagesCount: Int) {
        if (usagesCount == 0 || project == null) return
        informSingletonManager
            .notify(JupyterKotlinBundle.message("kotlin.jupyter.settings.title"),
                    JupyterKotlinBundle.message("kotlin.jupyter.refactor.changed.definition", usagesCount),
                    project
            )
    }

    fun showRerunActionNeeded(project: Project?) {
        if (project == null) return
        informSingletonManager
            .notify(JupyterKotlinBundle.message("kotlin.jupyter.settings.title"),
                    JupyterKotlinBundle.message("kotlin.jupyter.refactor.changed.definition.rerun"),
                    project)
    }

    //fun showErrorHint(project: Project, editor: Editor, @NlsContexts.DialogMessage message: String, @NlsContexts.DialogTitle title: String) {
    //    KotlinSurrounderUtils.showErrorHint(project, editor, message, title, null)
    //}
}