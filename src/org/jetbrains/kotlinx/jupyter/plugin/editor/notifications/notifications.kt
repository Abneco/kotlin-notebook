// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.notifications

import com.intellij.ide.DataManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationTitle
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlinx.jupyter.plugin.debug.events.NotebookSessionEventListener
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.ui.KotlinNotebookConfigurable
import org.jetbrains.kotlinx.jupyter.plugin.util.getCurrentEditorOrNull
import org.jetbrains.kotlinx.jupyter.plugin.util.getKotlinNotebookVirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.util.getLastActiveFileEditor
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterEditorActionsUtils.getEditor
import org.jetbrains.plugins.notebooks.jupyter.editor.createAnActionEvent

@Suppress("DialogTitleCapitalization")
@get:NotificationTitle
private val kotlinNotebookTitle get() = KotlinNotebookBundle.message("kotlin.jupyter.settings.title")

private const val kotlinNotebookSessionNotificationGroup = "Kotlin Notebook session info"

internal interface NotebookNotificationShower {
    sealed class NotificationTarget

    fun showNotification(project: Project?, mark: NotificationTarget, @NlsSafe additionalMsg: String = "")
}

abstract class NotebookNotificationFactoryBase : NotebookNotificationShower {
    protected val sessionInfoNotifier = SingletonNotificationManager(kotlinNotebookSessionNotificationGroup, NotificationType.INFORMATION)
    protected val sessionWarnNotifier = SingletonNotificationManager(kotlinNotebookSessionNotificationGroup, NotificationType.WARNING)

    protected fun prepareEmbeddedActionContext(project: Project, providedAction: AnActionEvent?): DataContext {
        if (providedAction != null && providedAction.getEditor() != null) {
            return providedAction.dataContext
        }

        val editor = project.getCurrentEditorOrNull()
        return if (editor != null) {
            DataManager.getInstance().getDataContext(editor.component)
        } else DataContext { dataId ->
            when (dataId) {
                PlatformDataKeys.PROJECT.name -> project
                PlatformDataKeys.EDITOR.name -> providedAction?.dataContext?.getLastActiveFileEditor()
                else -> null
            }
        }
    }
}


internal class NotebookKernelRelatedNotificationFactory() : NotebookNotificationFactoryBase() {
    sealed class DependencyStatus : NotebookNotificationShower.NotificationTarget() {
        data object Outdated : DependencyStatus()
        data object Absent : DependencyStatus()
        data object AbsentInitial : DependencyStatus()
        data object InconsistentJDK : DependencyStatus()
    }

    sealed class KernelStatus : NotebookNotificationShower.NotificationTarget() {
        data object SessionRestart : DependencyStatus()
        data object SessionRunModeChanged : KernelStatus()
    }

    override fun showNotification(project: Project?, mark: NotebookNotificationShower.NotificationTarget, @NlsSafe additionalMsg: String) {
        if ((mark !is DependencyStatus && mark !is KernelStatus) || project == null) return

        when (mark) {
            is DependencyStatus.Outdated -> {
                sessionWarnNotifier.notify(
                    kotlinNotebookTitle,
                    KotlinNotebookBundle.message("kotlin.jupyter.dependencies.build.error.outdated"),
                    project
                )
            }
            is DependencyStatus.Absent -> {
                sessionWarnNotifier.notify(
                    kotlinNotebookTitle,
                    KotlinNotebookBundle.message("kotlin.jupyter.dependencies.build.error.severe"),
                    project
                )
            }
            is DependencyStatus.AbsentInitial -> {
                // SingletonManager is not suitable if call it frequently
                Notifications.Bus.notify(
                    Notification(
                        kotlinNotebookTitle,
                        KotlinNotebookBundle.message("kotlin.jupyter.session.initial.setup"),
                        NotificationType.INFORMATION
                    ).addAction(
                        ActionManager.getInstance().getAction("RestartKotlinNotebookHighlighting")
                    )
                )
            }
            is DependencyStatus.InconsistentJDK -> {
                sessionWarnNotifier.notify(
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
                sessionInfoNotifier.notify(
                    kotlinNotebookTitle,
                    KotlinNotebookBundle.message("kotlin.jupyter.session.restart"),
                    project
                )
            }
            is KernelStatus.SessionRunModeChanged -> {
                sessionInfoNotifier.notify(
                    kotlinNotebookTitle,
                    KotlinNotebookBundle.message("kotlin.jupyter.session.mode.changed"),
                    project
                ) { notification ->
                    notification.addAction(object : NotificationAction(KotlinNotebookBundle.message("action.RestartKotlinNotebookSession.text")) {
                        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                            val notebookFile = e.getKotlinNotebookVirtualFile()
                            if (notebookFile != null) {
                                val restartAction = ActionManager.getInstance().getAction("JupyterRestartKernelAction")

                                restartAction.apply {
                                    actionPerformed(
                                        createAnActionEvent(
                                            prepareEmbeddedActionContext(project, e)
                                        )
                                    )

                                    project.messageBus.syncPublisher(NotebookSessionEventListener.TOPIC)
                                        .sessionRestartedAfterRunModeChanged(notebookFile)
                                }
                            }

                            notification.expire()
                        }
                    })
                }
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

    fun showSessionModeChanged(project: Project) =
        showNotification(project, KernelStatus.SessionRunModeChanged)
}


internal class NotebookUsageRelatedNotificationFactory() : NotebookNotificationFactoryBase() {
    sealed class ActionRelated : NotebookNotificationShower.NotificationTarget() {
        data object ByteCodeRefactoring : ActionRelated()
        data object RerunActionNeeded : ActionRelated()
        data object UsagesRefactoring : ActionRelated()
    }

    override fun showNotification(project: Project?, mark: NotebookNotificationShower.NotificationTarget, @NlsSafe additionalMsg: String) {
        if (mark !is ActionRelated || project == null) return

        when (mark) {
            is ActionRelated.ByteCodeRefactoring -> {
                sessionWarnNotifier.notify(
                    kotlinNotebookTitle,
                    KotlinNotebookBundle.message("kotlin.jupyter.refactor.compiled.script"),
                    project
                )
            }
            is ActionRelated.RerunActionNeeded -> {
                sessionInfoNotifier.notify(
                    kotlinNotebookTitle,
                    KotlinNotebookBundle.message("kotlin.jupyter.refactor.changed.definition.rerun"),
                    project
                )
            }
            is ActionRelated.UsagesRefactoring -> {
                sessionInfoNotifier
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
