package org.jetbrains.kotlinx.jupyter.plugin.language.meta.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.JKTMetaFileType
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.JupyterKtMetaLanguage

class JKTMetaPSIFile(fileViewProvider: FileViewProvider) : PsiFileBase(fileViewProvider, JupyterKtMetaLanguage) {
    override fun getFileType(): FileType {
        return JKTMetaFileType
    }

    override fun toString(): String {
        return "Jupyter Kotlin meta file"
    }
}
