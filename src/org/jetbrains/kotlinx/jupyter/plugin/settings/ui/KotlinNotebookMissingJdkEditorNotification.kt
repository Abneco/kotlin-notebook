// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.settings.ui

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookMissingJdkService
import org.jetbrains.kotlinx.jupyter.plugin.settings.KotlinNotebookProjectOptionsProvider
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import java.util.function.Function
import javax.swing.JComponent

class KotlinNotebookMissingJdkEditorNotification : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!file.isKotlinNotebook) return null

        project.service<KotlinNotebookMissingJdkService>() // init service

        val optionsProvider = KotlinNotebookProjectOptionsProvider.getInstance(project)
        if (optionsProvider.jdk.getPath(project) != null) return null

        return Function {
            val panel = EditorNotificationPanel(HintUtil.WARNING_COLOR_KEY, EditorNotificationPanel.Status.Error)

            val jdkName = optionsProvider.jdkName
            val message = if (jdkName == null) {
                KotlinNotebookBundle.message("kotlin.jupyter.missing.jdk.notification.not.selected.text")
            } else {
                KotlinNotebookBundle.message("kotlin.jupyter.missing.jdk.notification.not.found.text", jdkName)
            }
            panel.text(message)

            panel.createActionLabel(
                KotlinNotebookBundle.message("kotlin.jupyter.missing.jdk.select.jdk.action"),
                "ShowKotlinNotebookPreferencesAction",
                false
            )

            panel
        }
    }
}