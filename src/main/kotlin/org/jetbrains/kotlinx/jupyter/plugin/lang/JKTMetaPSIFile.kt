package org.jetbrains.kotlinx.jupyter.plugin.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider

class JKTMetaPSIFile(fileViewProvider: FileViewProvider) : PsiFileBase(fileViewProvider, JupyterKtMetaLanguage) {
    override fun getFileType(): FileType {
        return JKTMetaFileType
    }

    override fun toString(): String {
        return "Jupyter Kotlin meta file"
    }
}
