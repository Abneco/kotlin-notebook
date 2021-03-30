package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterNotebookImpl
import kotlin.concurrent.write

/**
 * [JupyterKotlinIntoNotebookInjector] injects Kotlin code into notebook which
 * is known to be Kotlin one.
 *
 * Note that this approach is universal: we may inject Jupyter-Kotlin code into
 * any other file (for example into the other formats of notebooks). So, all the main logic
 * should go to project-level and file-level compile services, injectors are responsible
 * only for injecting the code.
 */
class JupyterKotlinIntoNotebookInjector(project: Project) : MultiHostInjector {
    private val projectCompilerService = JupyterCompilerService.getInstance(project)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, element: PsiElement) {
        if (element !is JupyterNotebookImpl) return

        val containingFile = element.originalElement.containingFile
        val virtualFile = containingFile.originalFile.virtualFile as? NotebookVirtualFile ?: return

        val compilerService = projectCompilerService.get(virtualFile)

        // This usage of the service lock should be rewritten
        compilerService.compileLock.write {
            compilerService.nbInjectionHosts.clear()
            for (cell in element.psiCellList) {
                registrar.startInjecting(Language.findLanguageByID("kotlin")!!, projectCompilerService.fileExtension)
                val host = NotebookCellInjectionHost(cell)
                compilerService.nbInjectionHosts.add(host)
                compilerService.codeRanges(cell).forEach {
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
