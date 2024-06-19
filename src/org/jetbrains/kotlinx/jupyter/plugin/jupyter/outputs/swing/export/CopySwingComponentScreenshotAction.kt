// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.swing.export

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.async
import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceAnd
import org.jetbrains.kotlinx.jupyter.api.InMemoryMimeTypes
import org.jetbrains.kotlinx.jupyter.api.takeScreenshot
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.plots.export.createImageDataTransferable
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.swing.SwingComponent
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.swing.SwingOutputDataKey
import org.jetbrains.kotlinx.jupyter.plugin.jupyter.outputs.swing.SwingOutputDataKeyExtractor
import org.jetbrains.kotlinx.jupyter.plugin.util.KotlinNotebookPluginScope
import org.jetbrains.kotlinx.jupyter.plugin.util.firstAncestorOfType
import org.jetbrains.kotlinx.jupyter.plugin.util.runSafely
import org.jetbrains.plugins.notebooks.core.api.getNotebookCellAndFile
import org.jetbrains.plugins.notebooks.core.impl.actions.NotebookEditorActionBase
import org.jetbrains.plugins.notebooks.core.impl.file.notebook
import org.jetbrains.plugins.notebooks.jupyter.editor.getCellIndex
import org.jetbrains.plugins.notebooks.jupyter.editor.outputs.NotebookObjectOutputDataKeyExtractor
import org.jetbrains.plugins.notebooks.jupyter.nbformat.JupyterDisplayDataOutput
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame

/**
 * Action that makes it possible to copy a screenshot of a [SwingComponent] to the clipboard.
 */
class CopySwingComponentScreenshotAction : NotebookEditorActionBase() {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val outputs = getOutputs(event).takeIf { isActionApplicable(it) } ?: return
        doCopyScreenshot(outputs, project)
    }

    private fun isActionApplicable(outputs: List<SwingOutputDataKey>): Boolean {
        return outputs.size == 1
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        super.update(event)
        val outputs = getOutputs(event)
        event.presentation.isEnabledAndVisible = isActionApplicable(outputs)
    }

    private fun doCopyScreenshot(outputs: List<SwingOutputDataKey>, project: Project) {
        val output = outputs.singleOrNull() ?: return
        KotlinNotebookPluginScope.getForProject(project).async {
            runSafely (
                {
                    when(val component = output.component) {
                        is JFrame -> component.takeScreenshot()
                        is JDialog -> component.takeScreenshot()
                        is JComponent -> component.takeScreenshot()
                        else -> throw IllegalStateException("Unsupported component: $component")
                    }?.let { screenshot: BufferedImage ->
                        copyScreenshotToClipboard(project, screenshot)
                    }
                },
                { throwable ->
                    showSwingScreenshotFailedNotification(throwable)
                }
            )
        }
    }
    private fun getOutputs(event: AnActionEvent): List<SwingOutputDataKey> {
        val contextComponent = event.dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
        val swingComponent = contextComponent?.firstAncestorOfType<SwingComponent>()
        if (swingComponent != null) {
            return listOfNotNull(swingComponent.dataKey)
        }

        val project = event.project
        val (psiCell, notebookVirtualFile) = event.dataContext.getNotebookCellAndFile() ?: return emptyList()
        val cellIndex = psiCell.getCellIndex()
        val notebook = notebookVirtualFile.notebook
        val jupyterCell = notebook.computeCells()[cellIndex]
        val outputs = jupyterCell.outputs ?: return emptyList()
        val extractor = NotebookObjectOutputDataKeyExtractor.EP_NAME.findExtension(SwingOutputDataKeyExtractor::class.java) ?: return emptyList()
        return outputs.outputs.filterIsInstanceAnd<JupyterDisplayDataOutput> { output ->
            output.data.has(InMemoryMimeTypes.SWING)
        }.mapNotNull { letPlotOutput ->
            extractor.extractKey(project, notebookVirtualFile, letPlotOutput.data.toV4Json(), null)
        }
    }

    @RequiresBackgroundThread
    private fun copyScreenshotToClipboard(project: Project, image: BufferedImage) {
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        val transferable = createImageDataTransferable(project, out.toByteArray(), "screenshot.png")
        CopyPasteManager.getInstance().setContents(transferable)
    }
}
