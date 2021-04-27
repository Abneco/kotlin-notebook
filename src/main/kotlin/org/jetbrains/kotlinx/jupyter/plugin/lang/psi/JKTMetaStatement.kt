package org.jetbrains.kotlinx.jupyter.plugin.lang.psi

import com.intellij.psi.PsiElement
import org.jetbrains.kotlinx.jupyter.plugin.psi.meta.JKTMetaNewlineOrEof
import org.jetbrains.kotlinx.jupyter.plugin.psi.meta.JKTMetaStatementId

interface JKTMetaStatement : PsiElement {
    fun getStatementId(): JKTMetaStatementId
    fun getNewlineOrEof(): JKTMetaNewlineOrEof
}
