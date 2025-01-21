// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.ui

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookMissingJdkService
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookProjectOptionsProvider
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

class KotlinNotebookMissingJdkEditorNotification : EditorNotificationProvider, DumbAware {
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