package org.jetbrains.kotlin.jupyter.plugin

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterNotebookImpl
import kotlin.concurrent.write
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.fileExtension

class JupyterKotlinIntoNotebookInjector(project: Project) : MultiHostInjector {
    private val compilerService = project.service<JupyterCompilerService>()

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, element: PsiElement) {
        if (element !is JupyterNotebookImpl) return

        val configuration = compilerService.jupyterCompileConfiguration
        val fileExtension = configuration[ScriptCompilationConfiguration.fileExtension] ?: "jupyter-kts"

        compilerService.compileLock.write {
            compilerService.nbInjectionHosts.clear()
            for (cell in element.cellList) {
                registrar.startInjecting(Language.findLanguageByID("kotlin")!!, fileExtension)
                val host = NotebookCellInjectionHost(cell)
                compilerService.nbInjectionHosts.add(host)
                compilerService.codeRanges(cell.text).forEach {
                    registrar.addPlace(null, null, host, it)
                }
                registrar.doneInjecting()
            }
        }
    }

    override fun elementsToInjectIn(): MutableList<out Class<out PsiElement>> {
        return mutableListOf(JupyterNotebookImpl::class.java)
    }
}
