// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.notebooks.core.impl.file.takeIfBackedNotebook
import org.jetbrains.plugins.notebooks.jupyter.nbformat.CELL_MARKER
import org.jetbrains.plugins.notebooks.jupyter.nbformat.MARKDOWN_CELL_SUFFIX
import org.jetbrains.plugins.notebooks.jupyter.nbformat.RAW_CELL_SUFFIX
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterFile
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterNotebook
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterPsiCellImpl
import java.util.concurrent.atomic.AtomicInteger

class JupyterKotlinIntoCellsInjector(project: Project) : MultiHostInjector {
    private val injectedCounter = AtomicInteger()
    private val projectCompilerService = JupyterCompilerService.getInstance(project)

    private val kotlinLanguage = projectCompilerService.language
    private val metaLanguage = JupyterKtMetaLanguage

    private val idsCache = mutableMapOf<PsiElement, Int>()

    @Synchronized
    fun getId(psiElement: PsiElement): Int {
        return idsCache.getOrPut(psiElement) { injectedCounter.incrementAndGet() }
    }

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, element: PsiElement) {
        if (element !is JupyterPsiCellImpl) return

        val containingFile = element.originalElement.containingFile
        val virtualFile = takeIfBackedNotebook(containingFile.originalFile.virtualFile) ?: return

        if (!virtualFile.isKotlinNotebook) return
        if (element.cellMarker.text.matches(NON_CODE_CELL_REGEX)) return

        val compilerService = projectCompilerService.getOrCreate(virtualFile)
        val actualNotebookCells = (containingFile as? JupyterFile)?.children?.firstOrNull { it is JupyterNotebook }
                            ?.children?.toSet() ?: emptySet()

        compilerService.updateInjectionHosts { hosts ->
            hosts.removeIf { it !in actualNotebookCells }
            hosts.add(element)

            val (ranges, isCommand) = compilerService.codeRanges(element)

            fun List<TextRange>.inject(language: Language, extension: String) {
                registrar.startInjecting(
                    language,
                    "${getId(element)}.$extension"
                )
                for (range in this) {
                    if (range.isEmpty) continue
                    registrar.addPlace(null, null, element, range)
                }
                registrar.doneInjecting()
            }

            try {
                ranges.codeRanges?.inject(kotlinLanguage, projectCompilerService.fileExtension)
                if ((ranges.magicRanges?.size ?: 0) > 1 || isCommand) {
                    ranges.magicRanges?.inject(metaLanguage, JKTMetaFileType.EXTENSION)
                }
            } catch (_: RuntimeException) {} // ignore concurrent change in NotebookVirtualFileSystem
        }
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> {
        return ELEMENTS_TO_INJECT
    }

    companion object {
        private val ELEMENTS_TO_INJECT = mutableListOf(JupyterPsiCellImpl::class.java)
        private val NON_CODE_CELL_REGEX = Regex("""$CELL_MARKER($MARKDOWN_CELL_SUFFIX|$RAW_CELL_SUFFIX)\n?""")
    }
}