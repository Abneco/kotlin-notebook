// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export

import com.intellij.ide.actions.OpenFileAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import java.io.File

private const val KANDY_NOTIFICATIONS_GROUP = "Kandy plot export"

fun showPlotExportFailedNotification(throwable: Throwable) {
    @NlsSafe
    val exceptionText = throwable.message.orEmpty()

    val notification = Notification(
        KANDY_NOTIFICATIONS_GROUP,
        KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.failed.notification.message"),
        exceptionText,
        NotificationType.ERROR
    )

    Notifications.Bus.notify(notification)
}

fun showPlotExportedNotification(file: File) {
    val notification = Notification(
        KANDY_NOTIFICATIONS_GROUP,
        KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.notification.message", file.name),
        NotificationType.INFORMATION
    )

    notification.addAction(
        NotificationAction.create(KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.notification.action.open")) { e ->
            val project = e.project ?: return@create
            OpenFileAction.openFile(file.absolutePath, project)
        }
    )

    Notifications.Bus.notify(notification)
}
