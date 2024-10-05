// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.swing.export

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle

private const val SWING_NOTIFICATIONS_GROUP = "Swing component export"

internal fun showSwingScreenshotFailedNotification(throwable: Throwable) {
    @NlsSafe
    val exceptionText = throwable.message.orEmpty()

    val notification = Notification(
        SWING_NOTIFICATIONS_GROUP,
        KotlinNotebookBundle.message("kotlin.notebook.outputs.swing.component.export.failed.notification.message"),
        exceptionText,
        NotificationType.ERROR
    )
    Notifications.Bus.notify(notification)
}
