// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.language.meta

import com.intellij.kotlin.jupyter.core.resources.i18n.KotlinNotebookBundle
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object JKTMetaFileType : LanguageFileType(JupyterKtMetaLanguage) {
    override fun getName() = "Jupyter Kotlin meta language"
    override fun getDescription() = KotlinNotebookBundle.message("meta.file.type.description")
    override fun getDefaultExtension() = EXTENSION
    override fun getIcon(): Icon? = null

    const val EXTENSION = "juktm"
}
