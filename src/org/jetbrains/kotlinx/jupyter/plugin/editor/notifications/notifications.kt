// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.notifications

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookConfigurable

internal object NotebookNotificationUtility {
    private fun prepareNotificationGroupTemplate() =
        NotificationGroupManager.getInstance().getNotificationGroup("Find Problems")

    private val informSessionSingletonManager = SingletonNotificationManager("Kotlin Notebook plugin updates", NotificationType.INFORMATION)
    private val informSingletonManager = SingletonNotificationManager("Find Problems", NotificationType.INFORMATION)
    private val informSingletonManagerWarning = SingletonNotificationManager("Find Problems", NotificationType.WARNING)

    private inline fun NotificationGroup.wrapActionInNotify(project: Project?, crossinline action: NotificationGroup.() -> Notification) =
        this.action().notify(project)

    fun showBytecodeRefactoringWarning(project: Project?) {
        prepareNotificationGroupTemplate().wrapActionInNotify(project) {
            createNotification(KotlinNotebookBundle.message("kotlin.jupyter.refactor.compiled.script"), NotificationType.WARNING)
            .setTitle(KotlinNotebookBundle.message("kotlin.jupyter.settings.title"))
        }
    }

    fun showExistingUsagesMessage(project: Project?, usagesCount: Int) {
        if (usagesCount == 0 || project == null) return
        informSingletonManager
            .notify(
                KotlinNotebookBundle.message("kotlin.jupyter.settings.title"),
                KotlinNotebookBundle.message("kotlin.jupyter.refactor.changed.definition", usagesCount),
                project
            )
    }

    fun showRerunActionNeeded(project: Project?) {
        if (project == null) return
        informSingletonManager
            .notify(
                KotlinNotebookBundle.message("kotlin.jupyter.settings.title"),
                KotlinNotebookBundle.message("kotlin.jupyter.refactor.changed.definition.rerun"),
                project)
    }

    fun showOutdatedDependencies(project: Project?) {
        if (project == null) return
        informSingletonManagerWarning
            .notify(
                KotlinNotebookBundle.message("kotlin.jupyter.settings.title"),
                KotlinNotebookBundle.message("kotlin.jupyter.dependencies.build.error.outdated"),
                project)
    }

    fun showAbsentDependencies(project: Project?) {
        if (project == null) return
        prepareNotificationGroupTemplate().wrapActionInNotify(project) {
            createNotification(KotlinNotebookBundle.message("kotlin.jupyter.dependencies.build.error.severe"), NotificationType.WARNING)
                .setTitle(KotlinNotebookBundle.message("kotlin.jupyter.settings.title"))
        }
    }

    fun showKernelRestart(project: Project?) {
        if (project == null) return
        informSessionSingletonManager
            .notify(
                KotlinNotebookBundle.message("kotlin.jupyter.settings.title"),
                KotlinNotebookBundle.message("kotlin.jupyter.session.restart"),
                project)
    }

    fun showAbsentInitialBaseDependenciesInfo(project: Project) {
        informSingletonManager
          .notify(
              KotlinNotebookBundle.message("kotlin.jupyter.settings.title"),
              KotlinNotebookBundle.message("kotlin.jupyter.session.initial.setup"),
              project)
    }

    fun showKernelJDKInconsistentError(project: Project, @NlsSafe loaderError: String = "") {
        informSingletonManagerWarning.notify(
            KotlinNotebookBundle.message("kotlin.jupyter.settings.title"),
            KotlinNotebookBundle.message("kotlin.jupyter.session.classloader.error") +
          "\n" + loaderError, project
        ) { notification ->
            notification.addAction(object : NotificationAction(KotlinNotebookBundle.message("kotlin.jupyter.settings.JDK.action.preview")) {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, KotlinNotebookConfigurable::class.java) {
                        it.focusOn(KotlinNotebookBundle.message("kotlin.jupyter.settings.JDK.path"))
                    }
                }
            })
        }
    }

    //fun showErrorHint(project: Project, editor: Editor, @NlsContexts.DialogMessage message: String, @NlsContexts.DialogTitle title: String) {
    //    KotlinSurrounderUtils.showErrorHint(project, editor, message, title, null)
    //}
}