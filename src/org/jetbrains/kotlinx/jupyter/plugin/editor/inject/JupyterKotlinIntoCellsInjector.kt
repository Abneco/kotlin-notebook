// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.inject

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.JKTMetaFileType
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.JupyterKtMetaLanguage
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.JupyterCompilerService
import org.jetbrains.kotlinx.jupyter.plugin.scriptingSupport.KotlinCodeRangesProcessor
import org.jetbrains.kotlinx.jupyter.plugin.util.isKotlinNotebook
import org.jetbrains.plugins.notebooks.core.api.psi.NotebookPsiCell
import org.jetbrains.plugins.notebooks.core.impl.file.BackedNotebookVirtualFile
import org.jetbrains.plugins.notebooks.jupyter.nbformat.CELL_MARKER
import org.jetbrains.plugins.notebooks.jupyter.nbformat.nonCodeCellSuffixes
import org.jetbrains.plugins.notebooks.jupyter.psi.impl.JupyterPsiCellImpl
import java.util.concurrent.atomic.AtomicInteger

private val ELEMENTS_TO_INJECT = mutableListOf(JupyterPsiCellImpl::class.java)
private val NON_CODE_CELL_REGEX = Regex("""$CELL_MARKER(${nonCodeCellSuffixes.joinToString("|")})\n?""")

val NotebookPsiCell.isNonCode get() = cellMarker.text.matches(NON_CODE_CELL_REGEX)

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
        val virtualFile = containingFile.originalFile.virtualFile?.let(BackedNotebookVirtualFile::takeIfBacked) ?: return

        if (!virtualFile.file.isKotlinNotebook) return
        if (element.isNonCode) return

        val (ranges, isCommand) = KotlinCodeRangesProcessor.codeRanges(element)

        fun List<TextRange>.inject(language: Language, extension: String, skipEmpty: Boolean) {
            val rangesToInject = if (skipEmpty) filterNot { it.isEmpty } else this
            if (rangesToInject.isEmpty()) return

            registrar.startInjecting(
                language,
                "${getId(element)}.$extension"
            )
            for (range in rangesToInject) {
                registrar.addPlace(null, null, element, range)
            }
            registrar.doneInjecting()
        }

        try {
            ranges.codeRanges.inject(kotlinLanguage, projectCompilerService.fileExtension, skipEmpty = false)
            if (ranges.magicRanges.size > 1 || isCommand) {
                ranges.magicRanges.inject(metaLanguage, JKTMetaFileType.EXTENSION, skipEmpty = true)
            }
        } catch (e: RuntimeException) {
            // ignore concurrent change in NotebookVirtualFileSystem
            if (e is ProcessCanceledException) {
                throw e
            }
        }
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> {
        return ELEMENTS_TO_INJECT
    }
}
