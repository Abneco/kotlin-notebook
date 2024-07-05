// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.projectModel

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.HintHint
import com.intellij.ui.LightweightHint
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlinx.jupyter.plugin.resources.i18n.KotlinNotebookBundle
import org.jetbrains.kotlinx.jupyter.plugin.settings.registryFlag
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterNotebookDependencies
import org.jetbrains.plugins.notebooks.jupyter.connections.execution.notebook.JupyterRuntimeService
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterFileEditor
import java.awt.Font
import java.awt.Point
import javax.swing.event.HyperlinkEvent

@JvmInline
value class NotebookId(val virtualFile: VirtualFile)


@Service(Service.Level.PROJECT)
class JupyterKotlinOutdatedDependenciesNotificationService(val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
    private val notebooksWithNotUpToDateDependencies: MutableSet<NotebookId> = ConcurrentCollectionFactory.createConcurrentSet()
    private val notebooksWithScheduledHint: MutableSet<NotebookId> = ConcurrentCollectionFactory.createConcurrentSet()

    private var showHints by registryFlag("kotlin.notebook.outdated.dependencies.hints", true)

    init {
        project.messageBus.connect(this).subscribe(
            topic = FileEditorManagerListener.FILE_EDITOR_MANAGER,
            handler = object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val selectedEditor = event.newEditor as? JupyterFileEditor ?: return
                    val notebookId = NotebookId(selectedEditor.file)

                    if (notebooksWithScheduledHint.remove(notebookId)) {
                        if (showHints && !showHint(selectedEditor)) {
                            notebooksWithScheduledHint.add(notebookId)
                        }
                    }
                }
            }
        )
    }

    @RequiresEdt
    fun notify(notebook: NotebookId) {
        if (JupyterRuntimeService.getInstance(project).getSession(notebook.virtualFile) == null) {
            return
        }

        notebooksWithNotUpToDateDependencies.add(notebook)

        val fileEditorManager = FileEditorManager.getInstance(project)
        for (fileEditor in fileEditorManager.getEditors(notebook.virtualFile)) {
            JupyterNotebookDependencies.setNotUpToDate(fileEditor)
        }

        if (!showHints) return

        val selectedEditor = fileEditorManager.getSelectedEditors()
            .asSequence()
            .filterIsInstance<JupyterFileEditor>()
            .firstOrNull { it.file.isKotlinNotebook && it.file == notebook.virtualFile }

        if (selectedEditor == null || !showHint(selectedEditor)) {
            notebooksWithScheduledHint.add(notebook)
        }
    }

    /** Returns whether showing the hint was successful */
    @RequiresEdt
    private fun showHint(selectedEditor: JupyterFileEditor): Boolean {
        val button = selectedEditor.jupyterFileEditorToolbar?.getRestartKernelActionButton()
        if (button == null) return false

        lateinit var lightweightHint: LightweightHint
        lightweightHint = LightweightHint(HintUtil.createInformationLabel(
            /* text = */ KotlinNotebookBundle.message("kotlin.notebook.outdated.dependencies.hint.text"),
            /* hyperlinkListener = */ { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    showHints = false
                    lightweightHint.hide()
                }
            },
            /* mouseListener = */ null,
            /* updatedTextConsumer = */ null,
        ))

        val hint = HintHint(button, Point(button.width / 2, button.height))
            .setPreferredPosition(Balloon.Position.below)
            .setAwtTooltip(true)
            .setFont(StartupUiUtil.labelFont.deriveFont(Font.BOLD))
            .setBorderColor(HintUtil.getHintBorderColor())
            .setTextBg(HintUtil.getInformationColor())
            .setShowImmediately(true)
            .setExplicitClose(true)
            .setTextFg(if (StartupUiUtil.isDarkTheme) UIUtil.getLabelForeground() else UIUtil.getTextFieldForeground())
            .setStatus(HintHint.Status.Info)

        lightweightHint.show(
            /* parentComponent = */ button,
            /* x = */ 0,
            /* y = */ 0,
            /* focusBackComponent = */ null,
            /* hintHint = */ hint,
        )
        return true
    }

    fun notificationExpire(notebook: NotebookId) {
        notebooksWithScheduledHint.remove(notebook)
        if (notebooksWithNotUpToDateDependencies.remove(notebook)) {
            val fileEditorManager = FileEditorManager.getInstance(project)
            coroutineScope.launch(Dispatchers.EDT) {
                fileEditorManager.getEditors(notebook.virtualFile).forEach {
                    JupyterNotebookDependencies.resetUpToDate(it)
                }
            }
        }
    }

    override fun dispose() {
        notebooksWithNotUpToDateDependencies.clear()
        notebooksWithScheduledHint.clear()
    }
}