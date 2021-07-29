package org.jetbrains.kotlinx.jupyter.plugin.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object JKTMetaFileType : LanguageFileType(JupyterKtMetaLanguage) {
    override fun getName() = "Jupyter Kotlin meta language"
    override fun getDescription() = "Provides support for Jupyter Kotlin commands, magics etc."
    override fun getDefaultExtension() = EXTENSION
    override fun getIcon(): Icon? = null

    const val EXTENSION = "juktm"
}
