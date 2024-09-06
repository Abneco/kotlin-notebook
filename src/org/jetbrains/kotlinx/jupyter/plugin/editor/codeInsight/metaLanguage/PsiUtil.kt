// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlinx.jupyter.plugin.editor.codeInsight.metaLanguage

import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import org.jetbrains.kotlinx.jupyter.plugin.language.meta.psi.JKTMetaStatement

fun PsiElement.findMetaStatement(): JKTMetaStatement? {
    return findParentOfType<JKTMetaStatement>(strict = false)
}
