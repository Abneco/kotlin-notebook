package org.jetbrains.kotlinx.jupyter.plugin.lang.codeInsight

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlinx.jupyter.plugin.lang.JupyterKtMetaLanguage

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
