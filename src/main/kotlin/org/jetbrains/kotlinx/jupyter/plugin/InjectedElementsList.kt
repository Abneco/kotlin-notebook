package org.jetbrains.kotlinx.jupyter.plugin

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Type returned by [com.intellij.lang.injection.InjectedLanguageManager.getInjectedPsiFiles]
 * This is a list of pairs in which each [PsiElement] is a [com.intellij.psi.PsiFile] (in our
 * case it is [org.jetbrains.kotlin.psi.KtFile]) and [TextRange] is a range in host file in that
 * this PSI file is injected.
 */
typealias InjectedElementsList = List<Pair<PsiElement, TextRange>>
