// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.language.meta.psi

import com.intellij.kotlin.jupyter.core.psi.meta.JKTMetaNewlineOrEof
import com.intellij.kotlin.jupyter.core.psi.meta.JKTMetaStatementId
import com.intellij.psi.PsiElement

interface JKTMetaStatement : PsiElement {
    fun getStatementId(): JKTMetaStatementId
    fun getNewlineOrEof(): JKTMetaNewlineOrEof
}
