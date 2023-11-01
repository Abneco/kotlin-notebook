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
import com.intellij.openapi.util.NlsContexts.NotificationTitle
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.ui.KotlinNotebookConfigurable

@Suppress("DialogTitleCapitalization")
@get:NotificationTitle
private val kotlinNotebookTitle get() = KotlinNotebookBundle.message("kotlin.jupyter.settings.title")

internal interface NotebookNotificationShower {
    sealed class NotificationTarget
    fun showNotification(project: Project?, mark: NotificationTarget, @NlsSafe additionalMsg: String = "")
}

abstract class NotebookNotificationFactoryBase : NotebookNotificationShower {
    protected fun prepareNotificationGroupTemplate(): NotificationGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("Find Problems")

    protected inline fun NotificationGroup.wrapActionInNotify(project: Project?, crossinline action: NotificationGroup.() -> Notification) =
        this.action().notify(project)

    protected val informSingletonManager = SingletonNotificationManager("Find Problems", NotificationType.INFORMATION)
    protected val informSingletonManagerWarning = SingletonNotificationManager("Find Problems", NotificationType.WARNING)
    protected val informSessionSingletonManager = SingletonNotificationManager("Kotlin Notebook plugin updates", NotificationType.INFORMATION)

}


internal class NotebookKernelRelatedNotificationFactory() : NotebookNotificationFactoryBase() {
    sealed class DependencyStatus : NotebookNotificationShower.NotificationTarget() {
        object Outdated : DependencyStatus()
        object Absent : DependencyStatus()
        object AbsentInitial : DependencyStatus()
        object InconsistentJDK : DependencyStatus()
    }

    sealed class KernelStatus : NotebookNotificationShower.NotificationTarget() {
        object SessionRestart : DependencyStatus()
    }

    override fun showNotification(project: Project?, mark: NotebookNotificationShower.NotificationTarget, @NlsSafe additionalMsg: String) {
        if ((mark !is DependencyStatus && mark !is KernelStatus) || project == null) return

        when (mark) {
            is DependencyStatus.Outdated -> {
                informSingletonManagerWarning.notify(
                    kotlinNotebookTitle,
                    KotlinNotebookBundle.message("kotlin.jupyter.dependencies.build.error.outdated"),
                        project
                )
            }
            is DependencyStatus.Absent -> {
                prepareNotificationGroupTemplate().wrapActionInNotify(project) {
                    createNotification(KotlinNotebookBundle.message("kotlin.jupyter.dependencies.build.error.severe"), NotificationType.WARNING)
                        .setTitle(kotlinNotebookTitle)
                }
            }
            is DependencyStatus.AbsentInitial -> {
                informSingletonManager.notify(
                    kotlinNotebookTitle,
                    KotlinNotebookBundle.message("kotlin.jupyter.session.initial.setup"),
                        project
                )
            }
            is DependencyStatus.InconsistentJDK -> {
                informSingletonManagerWarning.notify(
                    kotlinNotebookTitle,
                    KotlinNotebookBundle.message("kotlin.jupyter.session.classloader.error") +
                            "\n" + additionalMsg, project
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
            is KernelStatus.SessionRestart -> {
                informSessionSingletonManager
                    .notify(
                        kotlinNotebookTitle,
                        KotlinNotebookBundle.message("kotlin.jupyter.session.restart"),
                        project)
            }
            else -> Unit
        }
    }

    // convenience methods
    fun showOutdatedDependencies(project: Project?) {
        showNotification(project, DependencyStatus.Outdated)
    }

    fun showAbsentDependencies(project: Project?) {
        showNotification(project, DependencyStatus.Absent)
    }

    fun showAbsentInitialBaseDependenciesInfo(project: Project?) {
        showNotification(project, DependencyStatus.AbsentInitial)
    }

    fun showKernelJDKInconsistentError(project: Project, @NlsSafe loaderError: String = "") {
        showNotification(project, DependencyStatus.InconsistentJDK, loaderError)
    }

    fun showKernelRestart(project: Project?) =
        showNotification(project, KernelStatus.SessionRestart)
}


internal class NotebookUsageRelatedNotificationFactory() : NotebookNotificationFactoryBase() {
    sealed class ActionRelated : NotebookNotificationShower.NotificationTarget() {
        object ByteCodeRefactoring : ActionRelated()
        object RerunActionNeeded : ActionRelated()
        object UsagesRefactoring : ActionRelated()
    }

    override fun showNotification(project: Project?, mark: NotebookNotificationShower.NotificationTarget, @NlsSafe additionalMsg: String) {
        if (mark !is ActionRelated || project == null) return

        when (mark) {
            is ActionRelated.ByteCodeRefactoring -> {
                prepareNotificationGroupTemplate().wrapActionInNotify(project) {
                    createNotification(KotlinNotebookBundle.message("kotlin.jupyter.refactor.compiled.script"), NotificationType.WARNING)
                        .setTitle(kotlinNotebookTitle)
                }
            }
            is ActionRelated.RerunActionNeeded -> {
                informSingletonManager.notify(
                   kotlinNotebookTitle,
                    KotlinNotebookBundle.message("kotlin.jupyter.refactor.changed.definition.rerun"),
                        project
                )
            }
            is ActionRelated.UsagesRefactoring -> {
                informSingletonManager
                    .notify(
                        kotlinNotebookTitle,
                        KotlinNotebookBundle.message("kotlin.jupyter.refactor.changed.definition", additionalMsg.toIntOrNull() ?: 0),
                        project
                    )
            }
        }
    }

    fun showRerunActionNeeded(project: Project?) =
        showNotification(project, ActionRelated.RerunActionNeeded)

    fun showBytecodeRefactoringWarning(project: Project?) {
        showNotification(project, ActionRelated.ByteCodeRefactoring)
    }

    fun showRefactoringExistingUsagesMessage(project: Project?, usagesCount: Int) {
        showNotification(project, ActionRelated.UsagesRefactoring, usagesCount.toString())
    }
}



internal object NotebookNotificationUtility {
    val kernelRelatedFactory = NotebookKernelRelatedNotificationFactory()
    val usageRelatedFactory = NotebookUsageRelatedNotificationFactory()


    //fun showErrorHint(project: Project, editor: Editor, @NlsContexts.DialogMessage message: String, @NlsContexts.DialogTitle title: String) {
    //    KotlinSurrounderUtils.showErrorHint(project, editor, message, title, null)
    //}
}
