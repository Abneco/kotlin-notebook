// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider
import org.jetbrains.plugins.notebooks.jupyter.JupyterLanguage
import org.jetbrains.plugins.notebooks.jupyter.getMarkdownLanguage
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterTemplateTypes

class JupyterKotlinFileViewProvider(
  manager: PsiManager,
  file: VirtualFile,
  eventSystemEnabled: Boolean
) :
  MultiplePsiFilesPerDocumentFileViewProvider(
        manager,
        file,
        eventSystemEnabled
    ), TemplateLanguageFileViewProvider {

    override fun getLanguages(): MutableSet<Language> = hashSetOf(baseLanguage, getMarkdownLanguage())
    override fun getTemplateDataLanguage(): Language = Language.ANY

    override fun getBaseLanguage(): Language = JupyterLanguage

    override fun cloneInner(fileCopy: VirtualFile) = JupyterKotlinFileViewProvider(manager, fileCopy, false)

    override fun createFile(lang: Language): PsiFile? {
        if (lang == Language.ANY) return null
        val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang)
        return when {
            lang === JupyterLanguage -> parserDefinition.createFile(this)
            lang === getMarkdownLanguage() -> (parserDefinition.createFile(this) as PsiFileImpl)
                .apply {
                    contentElementType = JupyterTemplateTypes.MARKDOWN_TEMPLATE
                }
            else -> null
        }
    }
}
