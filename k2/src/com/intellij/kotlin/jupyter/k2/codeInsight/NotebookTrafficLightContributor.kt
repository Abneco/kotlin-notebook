// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.codeInsight

//import com.intellij.kotlin.jupyter.k2.scriptingSupport.NotebookScriptConfigurationsManager
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer
import com.intellij.codeInsight.daemon.impl.TrafficLightRendererContributor
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.toBackedNotebookFile
import com.intellij.kotlin.jupyter.k2.scriptingSupport.NotebookScriptConfigurationsManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.io.storage.HeavyProcessLatch

internal class NotebookTrafficLightContributor : TrafficLightRendererContributor {
    override fun createRenderer(
        editor: Editor,
        file: PsiFile?
    ): TrafficLightRenderer? {
        val project = editor.project
        if (project == null || !editor.virtualFile.isKotlinNotebook) return null
        val backedNotebookVirtualFile = editor.virtualFile?.toBackedNotebookFile() ?: return null

        return UpdateTrafficLightRenderer(project, editor, backedNotebookVirtualFile)
    }

    class UpdateTrafficLightRenderer(
        project: Project, editor: Editor, private val notebookFile: BackedNotebookVirtualFile
    ) : TrafficLightRenderer(project, editor) {
        override fun getDaemonCodeAnalyzerStatus(severityRegistrar: SeverityRegistrar): DaemonCodeAnalyzerStatus {
            val status = super.getDaemonCodeAnalyzerStatus(severityRegistrar)

            if (NotebookScriptConfigurationsManager.getInstance(project).cache[notebookFile.file] == null) {
                status.reasonWhySuspended = KotlinNotebookBundle.message("kotlin.jupyter.highlighting.traffic.configuration.empty")
                status.heavyProcessType = HeavyProcessLatch.Type.Processing
            } else if (JupyterCompilerService.getForFile(project, notebookFile).needsConfigurationUpdate) {
                status.reasonWhySuspended = KotlinNotebookBundle.message("kotlin.jupyter.highlighting.traffic.configuration.update")
                status.heavyProcessType = HeavyProcessLatch.Type.Syncing
            }

            return status
        }
    }

}