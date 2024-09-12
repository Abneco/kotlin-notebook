// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.github.actions

import com.intellij.injected.editor.EditorWindow
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.jupyter.core.jupyter.editor.isJupyter
import com.intellij.jupyter.core.jupyter.helper.notebookJsonText
import com.intellij.notebooks.jupyter.core.jupyter.JupyterFileType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.github.DefaultGithubGistContentsCollector
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest

class KotlinNotebookGithubGistContentsCollector : DefaultGithubGistContentsCollector() {
    override fun getContentFromEditor(editor: Editor, file: VirtualFile?): List<GithubGistRequest.FileContent>? {
        val topLevelEditor = (editor as? EditorWindow)?.delegate ?: editor
        if (topLevelEditor.isJupyter) return null

        return super.getContentFromEditor(editor, file)
    }

    override fun getFileContentInternal(file: VirtualFile): String? {
        val topLevelFile = (file as? VirtualFileWindow)?.delegate ?: file
        if (topLevelFile.fileType == JupyterFileType) {
            return topLevelFile.notebookJsonText
        }

        return super.getFileContentInternal(file)
    }
}
