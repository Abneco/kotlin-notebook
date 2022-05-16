// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin.actions

import com.intellij.codeInsight.folding.impl.FoldingUpdate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.impl.file.assertBackedNotebook
import org.jetbrains.plugins.notebooks.jupyter.editor.JupyterEditorCustomizer

object KotlinJupyterEditorCustomizer : JupyterEditorCustomizer {
    override fun onEditorCreated(project: Project, editor: Editor, virtualFile: VirtualFile) {
        assertBackedNotebook(virtualFile)
        if (!virtualFile.isKotlinNotebook) return

        editor.putUserData(FoldingUpdate.INJECTED_CODE_FOLDING_ENABLED, false)
    }
}
