package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterNotebookImpl
import kotlin.concurrent.write

class JupyterKotlinIntoNotebookInjector(project: Project) : MultiHostInjector {
    private val projectCompilerService = JupyterCompilerService.getInstance(project)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, element: PsiElement) {
        if (element !is JupyterNotebookImpl) return

        val containingFile = element.originalElement.containingFile
        val virtualFile = containingFile.originalFile.virtualFile as? NotebookVirtualFile ?: return

        val compilerService = projectCompilerService.get(virtualFile)

        compilerService.compileLock.write {
            compilerService.nbInjectionHosts.clear()
            for (cell in element.psiCellList) {
                registrar.startInjecting(Language.findLanguageByID("kotlin")!!, projectCompilerService.fileExtension)
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
