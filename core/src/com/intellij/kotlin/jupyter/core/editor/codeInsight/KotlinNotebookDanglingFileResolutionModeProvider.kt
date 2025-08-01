// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.editor.codeInsight

import com.intellij.kotlin.jupyter.core.util.isInsideKotlinNotebookCodeCell
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.KaDanglingFileResolutionModeProvider

class KotlinNotebookDanglingFileResolutionModeProvider : KaDanglingFileResolutionModeProvider {
    override fun getDanglingFileResolutionMode(contextElement: PsiElement): KaDanglingFileResolutionMode? {
        if (!contextElement.isInsideKotlinNotebookCodeCell) return null

        return KaDanglingFileResolutionMode.IGNORE_SELF
    }
}
