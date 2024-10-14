// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight.metaLanguage

import com.intellij.kotlin.jupyter.core.language.meta.psi.JKTMetaStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType

fun PsiElement.findMetaStatement(): JKTMetaStatement? {
    return findParentOfType<JKTMetaStatement>(strict = false)
}
