// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.notifications

import com.intellij.ide.DataManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationTitle
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.ui.KotlinNotebookConfigurable
import org.jetbrains.kotlinx.jupyter.plugin.util.getOpenedKotlinNotebookEditors
import org.jetbrains.kotlinx.jupyter.plugin.util.toKotlinNotebookBackedFile
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterEditorActionsUtils
import org.jetbrains.plugins.notebooks.jupyter.actions.JupyterRestartKernelAction
import org.jetbrains.plugins.notebooks.jupyter.editor.createAnActionEvent
import java.awt.Component

@Suppress("DialogTitleCapitalization")
@get:NotificationTitle
private val kotlinNotebookTitle get() = KotlinNotebookBundle.message("kotlin.jupyter.settings.title")

private const val kotlinNotebookSessionNotificationGroup = "Kotlin Notebook session info"

internal interface NotebookNotificationViewManager {
    sealed class NotificationTarget

    fun showNotification(mark: NotificationTarget, @NlsSafe additionalMsg: String = "")
}

abstract class NotebookNotificationFactoryBase(
    protected val project: Project,
) : NotebookNotificationViewManager {
    protected val sessionInfoNotifier = SingletonNotificationManager(kotlinNotebookSessionNotificationGroup, NotificationType.INFORMATION)
    protected val sessionWarnNotifier = SingletonNotificationManager(kotlinNotebookSessionNotificationGroup, NotificationType.WARNING)
    protected open fun createSettingsListeners(): List<KotlinNotebookProjectOptionsProvider.Listener> = emptyList()

    fun registerPluginSettingsListeners(disposable: Disposable) {
        val optionsProvider = KotlinNotebookProjectOptionsProvider.getInstance(project)
        createSettingsListeners().forEach {
            optionsProvider.addListener(it, disposable)
        }
    }

    protected fun Component.createActionContextFromComponent(): DataContext {
        return DataManager.getInstance().getDataContext(this)
    }
}

internal class NotebookKernelRelatedNotificationFactory(project: Project) : NotebookNotificationFactoryBase(project) {
    sealed class DependencyStatus : NotebookNotificationViewManager.NotificationTarget() {
        data object Outdated : DependencyStatus()
        data object Absent : DependencyStatus()
        data object AbsentInitial : DependencyStatus()
        data object InconsistentJDK : DependencyStatus()
    }

    sealed class KernelStatus : NotebookNotificationViewManager.NotificationTarget() {
        data object SessionRestart : DependencyStatus()
        data object SessionRunModeChanged : KernelStatus()
    }

    private inner class NotebookKernelSettingsChangesListener() : KotlinNotebookProjectOptionsProvider.Listener {
        override fun onKernelRunModeChanged() {
            showNotification(KernelStatus.SessionRunModeChanged)
        }
    }

    override fun createSettingsListeners(): List<KotlinNotebookProjectOptionsProvider.Listener> {
        return listOf(NotebookKernelSettingsChangesListener())
    }

    override fun showNotification(mark: NotebookNotificationViewManager.NotificationTarget, @NlsSafe additionalMsg: String) {
        if (mark !is DependencyStatus && mark !is KernelStatus) return

        when (mark) {
            is DependencyStatus.Outdated -> showOutdatedDependencies()
            is DependencyStatus.Absent -> showAbsentDependencies()
            is DependencyStatus.AbsentInitial -> showAbsentInitialBaseDependenciesInfo()
            is DependencyStatus.InconsistentJDK -> showKernelJDKInconsistentError()
            is KernelStatus.SessionRestart -> showKernelRestart()
            is KernelStatus.SessionRunModeChanged -> showSessionRunModeChanged()
            else -> Unit
        }
    }

    // convenience methods
    fun showOutdatedDependencies() {
        sessionWarnNotifier.notify(
            kotlinNotebookTitle,
            KotlinNotebookBundle.message("kotlin.jupyter.dependencies.build.error.outdated"),
            project
        )
    }

    fun showAbsentDependencies() {
        sessionWarnNotifier.notify(
            kotlinNotebookTitle,
            KotlinNotebookBundle.message("kotlin.jupyter.dependencies.build.error.severe"),
            project
        )
    }

    fun showAbsentInitialBaseDependenciesInfo() {
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

    fun showKernelJDKInconsistentError(@NlsSafe loaderError: String = "") {
        sessionWarnNotifier.notify(
            kotlinNotebookTitle,
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

    fun showKernelRestart() =
        sessionInfoNotifier.notify(
            kotlinNotebookTitle,
            KotlinNotebookBundle.message("kotlin.jupyter.session.restart"),
            project
        )

    fun showSessionRunModeChanged() {
        val notebookEditors = project.getOpenedKotlinNotebookEditors()?.filter {
            val vFile = it.file.toKotlinNotebookBackedFile() ?: return@filter false
            JupyterCompilerService.getForFile(project, vFile).executedCellsCount != 0
        } ?: return

        val additionalMsg = if (notebookEditors.size == 1) {
            " in ${notebookEditors.first().file.name} file"
        } else {
            "s in ${notebookEditors.size} files"
        }

        val notification = Notification(
            kotlinNotebookTitle,
            KotlinNotebookBundle.message("kotlin.jupyter.session.mode.changed"),
            NotificationType.INFORMATION
        )

        Notifications.Bus.notify(
            notification.addAction(object : NotificationAction(
                KotlinNotebookBundle.message("action.RestartKotlinNotebookSession.text", additionalMsg)
            ) {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    val restartAction = JupyterEditorActionsUtils.getAction(JupyterRestartKernelAction::class.java)

                    notebookEditors.forEach { editor ->
                        restartAction.apply {
                            actionPerformed(
                                createAnActionEvent(
                                    editor.component.createActionContextFromComponent()
                                )
                            )
                        }
                    }

                    notification.expire()
                }
            })
        )
    }
}


internal class NotebookUsageRelatedNotificationFactory(project: Project) : NotebookNotificationFactoryBase(project) {
    sealed class ActionRelated : NotebookNotificationViewManager.NotificationTarget() {
        data object ByteCodeRefactoring : ActionRelated()
        data object RerunActionNeeded : ActionRelated()
        data object UsagesRefactoring : ActionRelated()
    }

    override fun showNotification(mark: NotebookNotificationViewManager.NotificationTarget, @NlsSafe additionalMsg: String) {
        if (mark !is ActionRelated) return

        when (mark) {
            is ActionRelated.ByteCodeRefactoring -> showBytecodeRefactoringWarning(project)
            is ActionRelated.RerunActionNeeded -> showRerunActionNeeded(project)
            is ActionRelated.UsagesRefactoring -> showRefactoringExistingUsagesMessage(project, additionalMsg.toIntOrNull() ?: 0)
        }
    }

    fun showRerunActionNeeded(project: Project?) {
        sessionInfoNotifier.notify(
            kotlinNotebookTitle,
            KotlinNotebookBundle.message("kotlin.jupyter.refactor.changed.definition.rerun"),
            project
        )
    }

    fun showBytecodeRefactoringWarning(project: Project?) {
        sessionWarnNotifier.notify(
            kotlinNotebookTitle,
            KotlinNotebookBundle.message("kotlin.jupyter.refactor.compiled.script"),
            project
        )
    }

    fun showRefactoringExistingUsagesMessage(project: Project?, usagesCount: Int) {
        sessionInfoNotifier.notify(
            kotlinNotebookTitle,
            KotlinNotebookBundle.message("kotlin.jupyter.refactor.changed.definition", usagesCount),
            project
        )
    }
}

@Service(Service.Level.PROJECT)
internal class NotebookNotificationUtility(project: Project) : Disposable {
    companion object {
        fun getInstance(project: Project): NotebookNotificationUtility = project.service()
    }

    val kernelRelatedFactory = NotebookKernelRelatedNotificationFactory(project)
    val usageRelatedFactory = NotebookUsageRelatedNotificationFactory(project)

    init {
        kernelRelatedFactory.registerPluginSettingsListeners(this)
        usageRelatedFactory.registerPluginSettingsListeners(this)
    }

    override fun dispose() {

    }

    //fun showErrorHint(project: Project, editor: Editor, @NlsContexts.DialogMessage message: String, @NlsContexts.DialogTitle title: String) {
    //    KotlinSurrounderUtils.showErrorHint(project, editor, message, title, null)
    //}
}
