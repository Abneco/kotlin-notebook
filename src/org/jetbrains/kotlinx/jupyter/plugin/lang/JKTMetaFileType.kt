package org.jetbrains.kotlinx.jupyter.plugin.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.kotlinx.jupyter.plugin.JupyterKotlinBundle
import javax.swing.Icon

object JKTMetaFileType : LanguageFileType(JupyterKtMetaLanguage) {
    override fun getName() = "Jupyter Kotlin meta language"
    override fun getDescription() = JupyterKotlinBundle.message("meta.file.type.description")
    override fun getDefaultExtension() = EXTENSION
    override fun getIcon(): Icon? = null

    const val EXTENSION = "juktm"
}
