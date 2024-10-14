// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.language.meta.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.kotlin.jupyter.core.language.meta.JKTMetaFileType
import com.intellij.kotlin.jupyter.core.language.meta.JupyterKtMetaLanguage
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
