// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight.metaLanguage

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.kotlin.jupyter.core.language.meta.JupyterKtMetaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class JKTMetaTypedHandlerDelegate : TypedHandlerDelegate() {
    override fun checkAutoPopup(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.language == JupyterKtMetaLanguage) {
            if (Character.isLetterOrDigit(c) || c == '%' || c == ':') {
                val controller = AutoPopupController.getInstance(project)
                controller.scheduleAutoPopup(editor)
            }
        }

        return super.checkAutoPopup(c, project, editor, file)
    }
}
