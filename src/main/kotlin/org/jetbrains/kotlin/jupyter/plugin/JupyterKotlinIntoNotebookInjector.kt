package org.jetbrains.kotlin.jupyter.plugin

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterNotebookImpl
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.fileExtension

class JupyterKotlinIntoNotebookInjector(val project: Project): MultiHostInjector {
    private val compilerService = project.service<JupyterCompilerService>()

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, element: PsiElement) {
        if (element !is JupyterNotebookImpl) return

        val configuration = compilerService.jupyterCompileConfiguration
        val fileExtension = configuration[ScriptCompilationConfiguration.fileExtension] ?: "jupyter-kts"

        for (cell in element.cellList) {
            registrar.startInjecting(Language.findLanguageByID("kotlin")!!, fileExtension)
            val host = NotebookCellInjectionHost(cell)
            val textRange = host.textRange
            val shiftedRange = textRange.shiftLeft(textRange.startOffset)
            registrar.addPlace(null, null, host, shiftedRange)
            registrar.doneInjecting()
        }
    }

    override fun elementsToInjectIn(): MutableList<out Class<out PsiElement>> {
        return mutableListOf(JupyterNotebookImpl::class.java)
    }
}