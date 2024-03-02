// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.jupyter.psi

import com.intellij.jupyter.core.jupyter.JupyterLanguage
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.util.InjectionUtils
import org.jetbrains.plugins.notebooks.jupyter.JupyterFileViewProvider
import org.jetbrains.plugins.notebooks.jupyter.getMarkdownLanguage
import org.jetbrains.plugins.notebooks.jupyter.psi.JupyterTemplateTypes

class JupyterKotlinFileViewProvider(
    manager: PsiManager,
    file: VirtualFile,
    eventSystemEnabled: Boolean
) :
    JupyterFileViewProvider(
        manager,
        file,
        eventSystemEnabled
    ) {

    init {
        InjectionUtils.setFormatOnlyInjectedCode(this, true)
    }

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
