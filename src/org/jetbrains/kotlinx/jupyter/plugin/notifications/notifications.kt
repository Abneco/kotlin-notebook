// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.notifications

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsContexts.NotificationTitle
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.settings.selectedKernelVersion
import org.jetbrains.kotlinx.jupyter.plugin.settings.ui.KotlinNotebookConfigurable
import org.jetbrains.kotlinx.jupyter.plugin.util.getOpenedKotlinNotebookEditors
import org.jetbrains.kotlinx.jupyter.plugin.util.toKotlinNotebookBackedFile
import com.intellij.jupyter.core.jupyter.actions.restartKernel
import java.util.function.Consumer

@Suppress("DialogTitleCapitalization")
@get:NotificationTitle
private val kotlinNotebookTitle get() = KotlinNotebookBundle.message("kotlin.jupyter.settings.title")

private const val kotlinNotebookSessionNotificationGroup = "Kotlin Notebook session info"

@Service(Service.Level.PROJECT)
internal class KotlinNotebookNotifications(private val project: Project) {
    private val infoNotifier = SingletonNotificationManager(kotlinNotebookSessionNotificationGroup, NotificationType.INFORMATION)
    private val warnNotifier = SingletonNotificationManager(kotlinNotebookSessionNotificationGroup, NotificationType.WARNING)

    // convenience methods
    fun showOutdatedDependencies() {
        warnNotifier.notebookNotify(
            KotlinNotebookBundle.message("kotlin.jupyter.dependencies.build.error.outdated"),
        )
    }

    fun showAbsentDependencies() {
        warnNotifier.notebookNotify(
            KotlinNotebookBundle.message("kotlin.jupyter.dependencies.build.error.severe"),
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
        warnNotifier.notebookNotify(
            KotlinNotebookBundle.message("kotlin.jupyter.session.classloader.error") + "\n" + loaderError
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
        infoNotifier.notebookNotify(
            KotlinNotebookBundle.message("kotlin.jupyter.session.restart"),
        )

    fun showSessionRunModeChanged() {
        val notebookEditors = project.getOpenedKotlinNotebookEditors()?.filter {
            val vFile = it.file.toKotlinNotebookBackedFile() ?: return@filter false
            JupyterCompilerService.getForFile(project, vFile).executedCellsCount != 0
        }?.ifEmpty { return } ?: return

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
                    notebookEditors.forEach { textEditor ->
                        restartKernel(textEditor.editor)
                    }

                    notification.expire()
                }
            })
        )
    }

    fun showRerunActionNeeded() {
        infoNotifier.notebookNotify(
            KotlinNotebookBundle.message("kotlin.jupyter.refactor.changed.definition.rerun"),
        )
    }

    fun showBytecodeRefactoringWarning() {
        warnNotifier.notebookNotify(
            KotlinNotebookBundle.message("kotlin.jupyter.refactor.compiled.script"),
        )
    }

    fun showRefactoringExistingUsagesMessage(usagesCount: Int) {
        infoNotifier.notebookNotify(
            KotlinNotebookBundle.message("kotlin.jupyter.refactor.changed.definition", usagesCount),
        )
    }

    fun showOutdatedKernelWarningIfNeeded() {
        val selectedVersion = project.selectedKernelVersion ?: return
        val buildVersion = currentKernelVersion

        if (selectedVersion >= buildVersion) return
        val options = KotlinNotebookProjectOptionsProvider.getInstance(project)
        if (options.ignoreOutdatedKernelVersion) return

        warnNotifier.notebookNotify(
            KotlinNotebookBundle.message("kotlin.notebook.kernel.version.warning.content", selectedVersion, buildVersion)
        ) { notification ->
            with(notification) {
                isSuggestionType = true

                addAction(object : NotificationAction(KotlinNotebookBundle.message("kotlin.notebook.kernel.version.warning.action.fix.text")) {
                    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                        options.kernelVersion = buildVersion.toMavenVersion()
                        notification.expire()
                    }
                })

                addAction(object : NotificationAction(KotlinNotebookBundle.message("kotlin.notebook.kernel.version.warning.action.ignore.text")) {
                    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                        options.ignoreOutdatedKernelVersion = true
                        notification.expire()
                    }
                })
            }
        }
    }

    private fun SingletonNotificationManager.notebookNotify(
        content: @NotificationContent String,
    ) = notify(
        kotlinNotebookTitle,
        content,
        project
    )

    private fun SingletonNotificationManager.notebookNotify(
        content: @NotificationContent String,
        customizer: Consumer<Notification>,
    ) = notify(
        kotlinNotebookTitle,
        content,
        project,
        customizer,
    )

    companion object {
        fun getInstance(project: Project): KotlinNotebookNotifications = project.service()
    }
}

internal val Project.notebookNotifications get() = KotlinNotebookNotifications.getInstance(this)