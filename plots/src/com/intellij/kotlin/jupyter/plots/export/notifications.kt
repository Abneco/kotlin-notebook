// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.plots.export

import com.intellij.ide.actions.OpenFileAction
import com.intellij.ide.actions.RevealFileAction
import com.intellij.kotlin.jupyter.plots.i18n.KotlinNotebookPlotsBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.util.NlsSafe
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

private const val KANDY_NOTIFICATIONS_GROUP = "Kandy plot export"
private val kandyGroup get() = NotificationGroupManager.getInstance().getNotificationGroup(KANDY_NOTIFICATIONS_GROUP)

fun showPlotExportFailedNotification(throwable: Throwable) {
    val notification = kandyGroup.createNotification(
        KotlinNotebookPlotsBundle.message("kotlin.notebook.outputs.kandy.export.failed.notification.message"),
        throwable.asDescription(),
        NotificationType.ERROR
    )

    Notifications.Bus.notify(notification)
}

/**
 * Shows the notification after a plot saving process is finished.
 *
 * If a single plot was successfully saved, it suggests opening the file containing the plot.
 * If a single plot export failed, it shows the corresponding error stacktrace.
 * If multiple plots were exported, it shows the first failure if any
 * and suggests opening the folder containing new files (if any).
 *
 * @param savedFiles The collection of files that were successfully saved.
 * @param skippedFiles The collection of files which were not saved because they already exist
 * @param errors The collection of errors that occurred during saving the plots.
 */
fun showPlotSaveNotification(
    savedFiles: Collection<Path>,
    skippedFiles: Collection<Path>,
    errors: Collection<Throwable>,
) {
    val allExportsSucceeded = errors.isEmpty() && skippedFiles.isEmpty()
    val allExportsFailed = errors.isNotEmpty() && savedFiles.isEmpty()
    val allExportsSkipped = !allExportsFailed && savedFiles.isEmpty()

    val singleFile = savedFiles.singleOrNull()?.takeIf { allExportsSucceeded }
    if (singleFile != null) {
        showPlotExportedNotification(singleFile)
        return
    }

    val singleError = errors.singleOrNull()?.takeIf { allExportsFailed }
    if (singleError != null) {
        showPlotExportFailedNotification(singleError)
        return
    }

    val notificationType = when {
        allExportsSucceeded -> NotificationType.INFORMATION
        allExportsSkipped -> NotificationType.WARNING
        else -> NotificationType.ERROR
    }
    val notificationTitle = when {
        allExportsSucceeded -> KotlinNotebookPlotsBundle.message("kotlin.notebook.outputs.kandy.export.all.succeeded.notification.message")
        allExportsFailed -> KotlinNotebookPlotsBundle.message("kotlin.notebook.outputs.kandy.export.all.failed.notification.message")
        allExportsSkipped -> KotlinNotebookPlotsBundle.message("kotlin.notebook.outputs.kandy.export.all.skipped.notification.message")
        else -> KotlinNotebookPlotsBundle.message("kotlin.notebook.outputs.kandy.export.some.failed.notification.message")
    }
    val notificationContent = errors.firstOrNull()?.asDescription()

    val notification = if (notificationContent == null) {
        kandyGroup.createNotification(notificationTitle, notificationType)
    } else {
        kandyGroup.createNotification(notificationTitle, notificationContent, notificationType)
    }

    notification.addPlotOpenAction(savedFiles)

    Notifications.Bus.notify(notification)
}

private fun showPlotExportedNotification(file: Path) {
    val notification = kandyGroup.createNotification(
        KotlinNotebookPlotsBundle.message("kotlin.notebook.outputs.kandy.export.notification.message", file.name),
        NotificationType.INFORMATION
    )

    notification.addPlotOpenAction(listOf(file))

    Notifications.Bus.notify(notification)
}

private fun Notification.addPlotOpenAction(files: Collection<Path>) {
    val file = files.firstOrNull() ?: return
    val action = if (files.size > 1) {
        NotificationAction.create(KotlinNotebookPlotsBundle.message("kotlin.notebook.outputs.kandy.export.notification.action.open.multiple")) { _ ->
            RevealFileAction.openFile(file)
        }
    } else {
        NotificationAction.create(KotlinNotebookPlotsBundle.message("kotlin.notebook.outputs.kandy.export.notification.action.open")) { e ->
            val project = e.project ?: return@create
            OpenFileAction.openFile(file.absolutePathString(), project)
        }
    }
    addAction(action)
}

@NlsSafe
private fun Throwable.asDescription(): String {
    return when(this) {
        is FileAlreadyExistsException -> {
            KotlinNotebookPlotsBundle.message("kotlin.notebook.outputs.kandy.export.failed.already.exists.message", file)
        }
        else -> stackTraceToString()
    }
}
