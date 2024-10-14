// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.notifications

import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.settings.selectedKernelVersion
import com.intellij.kotlin.jupyter.core.settings.ui.KotlinNotebookConfigurable
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
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
import java.util.concurrent.ConcurrentHashMap

@Suppress("DialogTitleCapitalization")
@get:NotificationTitle
private val kotlinNotebookTitle get() = KotlinNotebookBundle.message("kotlin.jupyter.settings.title")

private const val kotlinNotebookSessionNotificationGroup = "Kotlin Notebook session info"

@Service(Service.Level.PROJECT)
internal class KotlinNotebookNotifications(private val project: Project) {
    private enum class KotlinNotebookNotificationType(
        val notificationType: NotificationType,
    ) {
        OUTDATED_DEPENDENCIES(NotificationType.WARNING),
        OUTDATED_KERNEL_VERSION(NotificationType.WARNING),
        ABSENT_DEPENDENCIES(NotificationType.WARNING),
        ABSENT_INITIAL_BASE_DEPENDENCIES_INFO(NotificationType.INFORMATION),
        KERNEL_JDK_INCONSISTENT_ERROR(NotificationType.WARNING),
        KERNEL_RESTART(NotificationType.INFORMATION),
        KERNEL_RUN_MODE_CHANGED(NotificationType.INFORMATION),
        RERUN_ACTION_NEEDED(NotificationType.INFORMATION),
        BYTECODE_REFACTORING_WARNING(NotificationType.WARNING),
        REFACTORING_EXISTING_USAGES_MESSAGE(NotificationType.INFORMATION),
    }

    private val notificationSingletons = ConcurrentHashMap<KotlinNotebookNotificationType, SingletonNotificationManager>()

    // convenience methods
    fun showOutdatedDependencies() {
        notify(
            KotlinNotebookNotificationType.OUTDATED_DEPENDENCIES,
            KotlinNotebookBundle.message("kotlin.jupyter.dependencies.build.error.outdated")
        )
    }

    fun showAbsentDependencies() {
        notify(
            KotlinNotebookNotificationType.ABSENT_DEPENDENCIES,
            KotlinNotebookBundle.message("kotlin.jupyter.dependencies.build.error.severe")
        )
    }

    fun showAbsentInitialBaseDependenciesInfo() {
        // SingletonManager is not suitable if call it frequently
        notify(
            KotlinNotebookNotificationType.ABSENT_INITIAL_BASE_DEPENDENCIES_INFO,
            KotlinNotebookBundle.message("kotlin.jupyter.session.initial.setup")
        ) {
            addAction(
                ActionManager.getInstance().getAction("RestartKotlinNotebookHighlighting")
            )
        }
    }

    fun showKernelJDKInconsistentError(@NlsSafe loaderError: String = "") {
        notify(
            KotlinNotebookNotificationType.KERNEL_JDK_INCONSISTENT_ERROR,
            KotlinNotebookBundle.message("kotlin.jupyter.session.classloader.error") + "\n" + loaderError
        ) {
            addAction(
                object : NotificationAction(
                    KotlinNotebookBundle.message("kotlin.jupyter.settings.JDK.action.preview")
                ) {
                    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, KotlinNotebookConfigurable::class.java) {
                            it.focusOn(KotlinNotebookBundle.message("kotlin.jupyter.settings.JDK.path"))
                        }
                    }
                }
            )
        }
    }

    fun showKernelRestart() =
        notify(
            KotlinNotebookNotificationType.KERNEL_RESTART,
            KotlinNotebookBundle.message("kotlin.jupyter.session.restart")
        )

    fun showRerunActionNeeded() {
        notify(
            KotlinNotebookNotificationType.RERUN_ACTION_NEEDED,
            KotlinNotebookBundle.message("kotlin.jupyter.refactor.changed.definition.rerun")
        )
    }

    fun showBytecodeRefactoringWarning() {
        notify(
            KotlinNotebookNotificationType.BYTECODE_REFACTORING_WARNING,
            KotlinNotebookBundle.message("kotlin.jupyter.refactor.compiled.script")
        )
    }

    fun showRefactoringExistingUsagesMessage(usagesCount: Int) {
        notify(
            KotlinNotebookNotificationType.REFACTORING_EXISTING_USAGES_MESSAGE,
            KotlinNotebookBundle.message("kotlin.jupyter.refactor.changed.definition", usagesCount)
        )
    }

    fun showOutdatedKernelWarningIfNeeded() {
        val selectedVersion = project.selectedKernelVersion ?: return
        val buildVersion = currentKernelVersion

        if (selectedVersion >= buildVersion) return
        val options = KotlinNotebookProjectOptionsProvider.getInstance(project)
        if (options.ignoreOutdatedKernelVersion) return

        notify(
            KotlinNotebookNotificationType.OUTDATED_KERNEL_VERSION,
            KotlinNotebookBundle.message("kotlin.notebook.kernel.version.warning.content", selectedVersion, buildVersion)
        ) {
            isSuggestionType = true
            addAction(
                object : NotificationAction(
                    KotlinNotebookBundle.message("kotlin.notebook.kernel.version.warning.action.fix.text")
                ) {
                    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                        options.kernelVersion = buildVersion.toMavenVersion()
                        notification.expire()
                    }
                }
            )
            addAction(
                object : NotificationAction(
                    KotlinNotebookBundle.message("kotlin.notebook.kernel.version.warning.action.ignore.text")
                ) {
                    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                        options.ignoreOutdatedKernelVersion = true
                        notification.expire()
                    }
                }
            )
        }
    }

    /**
     * If you output multiple notifications of the SAME type in a row - only the last one is shown
     * If you output notifications of different types, they are shown independently
     */
    private fun notify(
        type: KotlinNotebookNotificationType,
        content: @NotificationContent String,
        customizer: Notification.() -> Unit = {},
    ) {
        val notificationManager = notificationSingletons.getOrPut(type) {
            SingletonNotificationManager(kotlinNotebookSessionNotificationGroup, type.notificationType)
        }
        notificationManager.clear()

        notificationManager.notify(
            kotlinNotebookTitle,
            content,
            project,
            customizer,
        )
    }

    companion object {
        fun getInstance(project: Project): KotlinNotebookNotifications = project.service()
    }
}

internal val Project.notebookNotifications get() = KotlinNotebookNotifications.getInstance(this)