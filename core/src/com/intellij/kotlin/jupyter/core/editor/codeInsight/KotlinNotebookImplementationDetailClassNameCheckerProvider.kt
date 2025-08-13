// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight

import com.intellij.kotlin.jupyter.core.debug.util.NOTEBOOK_COMPILED_CLASS_NAME_SUFFIX
import com.intellij.kotlin.jupyter.core.editor.find.isCompiledCellClassDeclaration
import com.intellij.kotlin.jupyter.core.util.isInsideKotlinNotebookCodeCell
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.extensions.ImplementationDetailClassNameChecker
import org.jetbrains.kotlin.idea.base.psi.extensions.ImplementationDetailClassNameCheckerProvider

class KotlinNotebookImplementationDetailClassNameCheckerProvider: ImplementationDetailClassNameCheckerProvider {
    override fun get(contextElement: PsiElement): ImplementationDetailClassNameChecker? {
        if (!contextElement.isInsideKotlinNotebookCodeCell && !isCompiledCellClassDeclaration(contextElement)) return null

        return Checker
    }

    object Checker: ImplementationDetailClassNameChecker {
        override fun isImplementationDetail(className: String): Boolean {
            return className.endsWith(NOTEBOOK_COMPILED_CLASS_NAME_SUFFIX)
        }
    }
}