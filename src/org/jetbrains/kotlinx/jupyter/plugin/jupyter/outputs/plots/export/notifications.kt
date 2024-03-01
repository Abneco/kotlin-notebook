// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export

import com.intellij.ide.actions.OpenFileAction
import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import java.io.File

private const val KANDY_NOTIFICATIONS_GROUP = "Kandy plot export"
private val kandyGroup get() = NotificationGroupManager.getInstance().getNotificationGroup(KANDY_NOTIFICATIONS_GROUP)

fun showPlotExportFailedNotification(throwable: Throwable) {
    val notification = kandyGroup.createNotification(
        KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.failed.notification.message"),
        throwable.asDescription(),
        NotificationType.ERROR
    )

    Notifications.Bus.notify(notification)
}

/**
 * Shows the notification after a plot saving process is finished.
 *
 * If a single plot was successfully saved, it suggests opening the file containing the plot.
 * If a single plot export failed, shows corresponding error stacktrace.
 * If multiple plots were exported, shows first failure if any
 * and suggests to open the folder containing new files (if any).
 *
 * @param files The collection of files that were successfully saved.
 * @param errors The collection of errors that occurred during saving the plots.
 */
fun showPlotSaveNotification(
    files: Collection<File>,
    errors: Collection<Throwable>,
) {
    val allExportsSucceeded = errors.isEmpty()
    val allExportsFailed = files.isEmpty()

    val singleFile = files.singleOrNull()?.takeIf { allExportsSucceeded }
    if (singleFile != null) {
        showPlotExportedNotification(singleFile)
        return
    }

    val singleError = errors.singleOrNull()?.takeIf { allExportsFailed }
    if (singleError != null) {
        showPlotExportFailedNotification(singleError)
        return
    }

    val notificationType = if (allExportsSucceeded) NotificationType.INFORMATION else NotificationType.ERROR
    val notificationTitle = when {
        allExportsSucceeded -> KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.all.succeeded.notification.message")
        allExportsFailed -> KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.all.failed.notification.message")
        else -> KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.some.failed.notification.message")
    }
    val notificationContent = errors.firstOrNull()?.asDescription()

    val notification = if (notificationContent == null) {
        kandyGroup.createNotification(notificationTitle, notificationType)
    } else {
        kandyGroup.createNotification(notificationTitle, notificationContent, notificationType)
    }

    notification.addPlotOpenAction(files)

    Notifications.Bus.notify(notification)
}

private fun showPlotExportedNotification(file: File) {
    val notification = kandyGroup.createNotification(
        KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.notification.message", file.name),
        NotificationType.INFORMATION
    )

    notification.addPlotOpenAction(listOf(file))

    Notifications.Bus.notify(notification)
}

private fun Notification.addPlotOpenAction(files: Collection<File>) {
    val file = files.firstOrNull() ?: return
    val action = if (files.size > 1) {
        NotificationAction.create(KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.notification.action.open.multiple")) { _ ->
            RevealFileAction.openFile(file)
        }
    } else {
        NotificationAction.create(KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.notification.action.open")) { e ->
            val project = e.project ?: return@create
            OpenFileAction.openFile(file.absolutePath, project)
        }
    }
    addAction(action)
}

private fun Throwable.asDescription(): @NlsSafe String {
    return when(this) {
        is FileAlreadyExistsException -> {
            KotlinNotebookBundle.message("kotlin.notebook.outputs.kandy.export.failed.already.exists.message", file)
        }
        else -> stackTraceToString()
    }
}
