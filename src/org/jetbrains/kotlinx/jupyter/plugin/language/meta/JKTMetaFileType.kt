// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.language.meta

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.kotlinx.jupyter.plugin.i18n.JupyterKotlinBundle
import javax.swing.Icon

object JKTMetaFileType : LanguageFileType(JupyterKtMetaLanguage) {
    override fun getName() = "Jupyter Kotlin meta language"
    override fun getDescription() = JupyterKotlinBundle.message("meta.file.type.description")
    override fun getDefaultExtension() = EXTENSION
    override fun getIcon(): Icon? = null

    const val EXTENSION = "juktm"
}
