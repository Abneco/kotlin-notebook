// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.inject

import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.jupyter.core.jupyter.nbformat.nonCodeCellSuffixes
import com.intellij.kotlin.jupyter.core.language.meta.JKTMetaFileType
import com.intellij.kotlin.jupyter.core.language.meta.JupyterKtMetaLanguage
import com.intellij.kotlin.jupyter.core.scriptingSupport.JupyterCompilerService
import com.intellij.kotlin.jupyter.core.scriptingSupport.KotlinCodeRangesProcessor
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.notebooks.psi.core.api.psi.NotebookPsiCell
import org.jetbrains.plugins.notebooks.psi.jupyter.lexer.JupyterNotebookCellHeader.CELL_MARKER
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.impl.JupyterPsiCellImpl
import java.util.concurrent.atomic.AtomicInteger

private val ELEMENTS_TO_INJECT = mutableListOf(JupyterPsiCellImpl::class.java)
private val NON_CODE_CELL_REGEX get() = Regex("""${CELL_MARKER}(${nonCodeCellSuffixes.joinToString("|")})\n?""")

val NotebookPsiCell.isNonCode: Boolean get() = cellMarker.text.matches(NON_CODE_CELL_REGEX)

class JupyterKotlinIntoCellsInjector(project: Project) : MultiHostInjector, DumbAware {
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

        val ranges = KotlinCodeRangesProcessor.codeRanges(element)

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
            ranges.magicRanges.inject(metaLanguage, JKTMetaFileType.EXTENSION, skipEmpty = true)

            for ((languageInfo, textRanges) in ranges.codeRanges) {
                val language: Language
                val extension: String
                if (languageInfo != null) {
                    language = Language.findLanguageByID(languageInfo.id) ?: continue
                    extension = languageInfo.extension
                } else {
                    language = kotlinLanguage
                    extension = projectCompilerService.fileExtension
                }
                textRanges.inject(language, extension, skipEmpty = false)
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
