// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.debug.evaluation

import com.intellij.debugger.engine.JavaDebuggerCodeFragmentFactory
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder
import com.intellij.kotlin.jupyter.core.util.getInjectedKtFiles
import com.intellij.kotlin.jupyter.core.util.isKotlinNotebook
import com.intellij.kotlin.jupyter.core.util.toBackedNotebookFile
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.notebooks.jupyter.core.jupyter.JupyterLanguage
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaCodeFragment
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinEvaluatorBuilder
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinK1CodeFragmentFactory
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.impl.JupyterSourceImpl

/**
 * Class is used for evaluating expressions during a running DebugSession.
 * Provides context for the following purposes:
 *      - Ensures correct code insight within code fragments.
 *      - Ensures that evaluations of code fragments are performed accurately.
 *
 * Kotlin Notebook, built upon Jupyter Notebook, lacks a default JVM-based context.
 *
 * Currently, this feature is not in use, as we don't have a Debug integration yet.
 */
class KotlinNotebookCodeFragmentFactory : JavaDebuggerCodeFragmentFactory() {
    private val ktCodeFragmentFactory = KotlinK1CodeFragmentFactory()
    private val ktEvaluator = KotlinEvaluatorBuilder

    override fun createPsiCodeFragmentImpl(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment? {
        return createPresentationPsiCodeFragment(item, context, project)
    }

    override fun createPresentationPsiCodeFragmentImpl(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment? {
        if (context == null) return null
        val injectedManager = InjectedLanguageManager.getInstance(project)
        val cellAsParent = context.parent.parent as? JupyterPsiCell

        if (context.context !is JupyterSourceImpl || cellAsParent == null) {
            return ktCodeFragmentFactory.createPresentationPsiCodeFragment(item, context, project)
        }

        val injectedElement = cellAsParent.getInjectedKtFiles(injectedManager).firstOrNull()
        val backedNotebook = context.containingFile.virtualFile.toBackedNotebookFile()

        if (injectedElement == null || backedNotebook == null) {
            return ktCodeFragmentFactory.createPresentationPsiCodeFragment(item, null, project)
        }

        val cellOffset = cellAsParent.textOffset
        val newContext = injectedElement.findElementAt(cellOffset + 1)
        return ktCodeFragmentFactory.createPresentationPsiCodeFragment(item, newContext, project)
    }

    override fun isContextAccepted(contextElement: PsiElement?): Boolean {
        return contextElement?.containingFile?.virtualFile?.isKotlinNotebook == true
    }

    override fun getFileType(): LanguageFileType {
        return JupyterLanguage.associatedFileType!!
    }

    override fun getEvaluatorBuilder(): EvaluatorBuilder {
        return ktEvaluator
    }
}