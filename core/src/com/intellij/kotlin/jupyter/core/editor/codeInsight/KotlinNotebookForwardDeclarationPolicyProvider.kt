// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight

import com.intellij.kotlin.jupyter.core.util.isKotlinNotebookCodeCell
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.ForwardDeclarationPolicyProvider

class KotlinNotebookForwardDeclarationPolicyProvider: ForwardDeclarationPolicyProvider {
    override fun requiresDeclarationBeforeUse(container: PsiElement): Boolean? {
        if (!container.isKotlinNotebookCodeCell) return null

        // In K2 REPL model, notebook cells are somewhat similar to function bodies,
        // so we should add declarations before their usage
        return true
    }
}
