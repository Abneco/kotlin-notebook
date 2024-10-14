// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.refactoring

import com.intellij.kotlin.jupyter.core.util.isInsideKotlinNotebook
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.PostInsertDeclarationCallback
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

class KotlinNotebookPostInsertDeclarationCallback : PostInsertDeclarationCallback {

    // Kotlin file must end with a newline in the notebook,
    // it will be joined with a subsequent cell otherwise
    override fun declarationInserted(declaration: PsiElement, targetContainer: PsiElement, psiFactory: KtPsiFactory) {
        if (targetContainer !is KtFile || !targetContainer.isInsideKotlinNotebook) return

        targetContainer.addAfter(psiFactory.createNewLine(1), declaration)
    }
}
