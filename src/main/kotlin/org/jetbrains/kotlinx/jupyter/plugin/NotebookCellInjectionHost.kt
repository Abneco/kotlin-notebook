package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.psi.ElementManipulators
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterPsiCell

class NotebookCellInjectionHost(cell: JupyterPsiCell) : JupyterPsiCell by cell, PsiLanguageInjectionHost {
    override fun isValidHost(): Boolean {
        return true
    }

    override fun updateText(text: String): PsiLanguageInjectionHost {
        return ElementManipulators.handleContentChange(this, text)
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<NotebookCellInjectionHost> {
        return LiteralTextEscaper.createSimple(this)
    }
}
