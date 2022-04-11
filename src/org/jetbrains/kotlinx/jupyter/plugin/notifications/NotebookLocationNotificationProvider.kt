// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.notifications

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotificationProvider.CONST_NULL
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.util.isInsideSourceRoot
import java.util.function.Function
import javax.swing.JComponent

class NotebookLocationNotificationProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?> {
        if (!file.isKotlinNotebook || !project.isInsideSourceRoot(file)) return CONST_NULL

        return Function { editor ->
            EditorNotificationPanel(editor).apply {
                text(JupyterKotlinBundle.message("kotlin.jupyter.text.move.notebook.out.of.source.root"))
                icon(AllIcons.General.Warning)
            }
        }
    }
}
