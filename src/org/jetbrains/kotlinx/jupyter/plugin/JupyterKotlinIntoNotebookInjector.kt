package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlinx.jupyter.plugin.file.isKotlinNotebook
import org.jetbrains.kotlinx.jupyter.plugin.lang.JKTMetaFileType
import org.jetbrains.kotlinx.jupyter.plugin.lang.JupyterKtMetaLanguage
import org.jetbrains.kotlinx.jupyter.plugin.scripting.JupyterKtScriptingSupport
import org.jetbrains.kotlinx.jupyter.plugin.util.LogSaver
import org.jetbrains.plugins.notebooks.core.impl.file.NotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.nbformat.CELL_MARKER
import org.jetbrains.plugins.notebooks.jupyter.nbformat.MARKDOWN_CELL_SUFFIX
import org.jetbrains.plugins.notebooks.jupyter.nbformat.RAW_CELL_SUFFIX
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterNotebookImpl
import java.util.concurrent.atomic.AtomicInteger

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
    private val injectedCounter = AtomicInteger()
    private val projectCompilerService = JupyterCompilerService.getInstance(project)
    private val scriptingSupport = JupyterKtScriptingSupport.getInstance(project)

    private val logger = LogSaver()

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, element: PsiElement) {
        if (element !is JupyterNotebookImpl) return

        val containingFile = element.originalElement.containingFile
        val virtualFile = containingFile.originalFile.virtualFile as? NotebookVirtualFile ?: return

        if (!virtualFile.isKotlinNotebook) return

        val compilerService = projectCompilerService.get(virtualFile)
        val kotlinLanguage = projectCompilerService.language
        val metaLanguage = JupyterKtMetaLanguage

        compilerService.updateInjectionHosts { hosts ->
            hosts.clear()
            for (cell in element.psiCellList) {
                if (cell.cellMarker.text.matches(NON_CODE_CELL_REGEX)) continue
                hosts.add(cell)

                val ranges = compilerService.codeRanges(cell)

                fun List<TextRange>.inject(language: Language, extension: String) {
                    registrar.startInjecting(
                        language,
                        "${injectedCounter.incrementAndGet()}.$extension"
                    )
                    forEach {
                        registrar.addPlace(null, null, cell, it)
                    }
                    registrar.doneInjecting()
                }

                ranges.codeRanges?.inject(kotlinLanguage, projectCompilerService.fileExtension)
                ranges.magicRanges?.inject(metaLanguage, JKTMetaFileType.EXTENSION)
            }
        }

        scriptingSupport.update()
    }

    override fun elementsToInjectIn(): MutableList<out Class<out PsiElement>> {
        return mutableListOf(JupyterNotebookImpl::class.java)
    }

    companion object {
        private val NON_CODE_CELL_REGEX = Regex("""$CELL_MARKER($MARKDOWN_CELL_SUFFIX|$RAW_CELL_SUFFIX)\n?""")
    }
}
