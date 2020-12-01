package org.jetbrains.kotlin.jupyter.plugin

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterNotebookImpl

class JupyterKotlinIntoNotebookInjector(val project: Project): MultiHostInjector {
    override fun getLanguagesToInject(registrar: MultiHostRegistrar, element: PsiElement) {
        if (element !is JupyterNotebookImpl) return

        element
    }

    override fun elementsToInjectIn(): MutableList<out Class<out PsiElement>> {
        return mutableListOf(JupyterNotebookImpl::class.java)
    }
}