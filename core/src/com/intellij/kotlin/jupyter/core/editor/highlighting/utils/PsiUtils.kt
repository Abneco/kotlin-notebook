// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.highlighting.utils

import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.TreeTraversal
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPackageDirective

internal fun numberOfNonWhiteSpaceLeaves(ktFile: KtFile): Int {
    return SyntaxTraverser.psiTraverser(ktFile)
        .traverse(TreeTraversal.LEAVES_DFS)
        .count { psiLeaf ->
            PsiUtilCore.getElementType(psiLeaf) != TokenType.WHITE_SPACE
                    && psiLeaf !is KtPackageDirective
        }
}