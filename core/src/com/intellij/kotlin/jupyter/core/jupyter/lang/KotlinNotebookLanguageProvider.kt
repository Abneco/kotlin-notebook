// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.jupyter.lang

import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.icons.AllIcons
import com.intellij.jupyter.core.jackson
import com.intellij.jupyter.core.jupyter.helper.backedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.lang.NotebookLanguageProvider
import com.intellij.jupyter.core.jupyter.nbformat.JupyterNotebook
import com.intellij.kotlin.jupyter.core.jupyter.actions.NotebookMode
import com.intellij.kotlin.jupyter.core.jupyter.actions.getNewKotlinNotebookSettings
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.settings.KotlinNotebookPerFileSettingsCache
import com.intellij.kotlin.jupyter.core.settings.asJson
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlinx.jupyter.config.notebookKernelSpec
import org.jetbrains.kotlinx.jupyter.config.notebookLanguageInfo
import javax.swing.Icon

/**
 * Provides a Kotlin language option for the "Change Notebook Language" action.
 * Uses the same metadata as defined in the kotlin.jupyter.empty.ipynb.ft template.
 */
class KotlinNotebookLanguageProvider : NotebookLanguageProvider {
    override val language: Language = KotlinLanguage.INSTANCE

    override val icon: Icon = AllIcons.Language.Kotlin

    override fun getLanguageMetadata(project: Project): ObjectNode {
        // Parse the kernel spec and language info from the Kotlin Jupyter library
        return jackson.createObjectNode().apply {
            putJsonTree(
                "kernelspec",
                Json.encodeToString(notebookKernelSpec)
            )
            putJsonTree(
                "language_info",
                Json.encodeToString(notebookLanguageInfo)
            )
            putJsonTree(
                "ktnbPluginMetadata",
                getNewKotlinNotebookSettings(project, NotebookMode.STANDARD).asJson()
            )
        }
    }

    override fun isApplicable(notebook: JupyterNotebook): Boolean {
        return !notebook.isKotlinNotebook
    }

    override fun afterMetadataChanged(project: Project, file: VirtualFile) {
        val backedFile = file.backedNotebookVirtualFile ?: return
        KotlinNotebookPerFileSettingsCache.getInstance(project).notebookEditorCreated(file)
        JupyterCompilerService.getInstance(project).getOrCreate(backedFile)
    }

    private fun ObjectNode.putJsonTree(key: String, treeAsString: String?) {
        if (treeAsString == null) return
        set<ObjectNode>(key, jackson.readTree(treeAsString))
    }

    private fun ObjectNode.putJsonTree(key: String, tree: ObjectNode?) {
        set<ObjectNode>(key, tree ?: return)
    }
}
