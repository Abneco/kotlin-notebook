// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.codeinsight.hints

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.kotlin.jupyter.core.editor.codeInsight.hints.PsiHostTypeHintsInvalidator
import com.intellij.kotlin.jupyter.k1.codeinsight.hints.NotebookTypeHintsRegistry.Companion.psiHostChainHintsRegistry
import com.intellij.kotlin.jupyter.k1.codeinsight.hints.NotebookTypeHintsRegistry.Companion.psiHostHintsRegistry
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.kotlin.idea.codeInsight.hints.HintType
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import org.jetbrains.plugins.notebooks.psi.jupyter.psi.JupyterPsiCell

sealed class NotebookTypeHintsRegistry<T: Collection<*>> {
    val data: MutableMap<PsiElement, T> = mutableMapOf()

    private var isValid: Boolean = true

    val isDataInsideValid: Boolean
        get() {
            isValid.ifFalse { return false }
            val first = data.entries.firstOrNull() ?: return true
            return first.key.containingFile.virtualFile.isValid
        }

    fun clear() {
        data.clear()
        isValid = true
    }

    fun markInvalid() {
        isValid = false
    }

    companion object {
        internal val psiHostHintsRegistry = Key.create<PsiHostTypeHintsRegistry>("jupyter.kotlin.inlay.hints.registry")
        val psiHostChainHintsRegistry: Key<PsiHostChainCallTypeHintsRegistry?> = Key.create("jupyter.kotlin.inlay.hints.chain.call.registry")
    }
}

class K1PsiHostTypeHintsInvalidator : PsiHostTypeHintsInvalidator {
    override fun invalidateTypeHintsRegistry(host: PsiLanguageInjectionHost) {
        if (host !is JupyterPsiCell) return
        synchronized(this) {
            host.getUserData(psiHostHintsRegistry)?.clear()
            host.getUserData(psiHostChainHintsRegistry)?.clear()
        }
    }
}

class PsiHostTypeHintsRegistry : NotebookTypeHintsRegistry<MutableSet<HintType>>() {
    companion object {
        fun getOrCreateTypeHintsRegistry(host: PsiLanguageInjectionHost): PsiHostTypeHintsRegistry = synchronized(host) {
            val stored = host.getUserData(psiHostHintsRegistry)
            if (stored == null) {
                host.putUserData(psiHostHintsRegistry, PsiHostTypeHintsRegistry())
                host.getUserData(psiHostHintsRegistry)!!
            } else stored
        }
    }
}

class PsiHostChainCallTypeHintsRegistry : NotebookTypeHintsRegistry<List<Pair<PsiElement, InlayPresentation>>>() {
    companion object {
        fun getOrCreateChainCallTypeHintsRegistry(host: PsiLanguageInjectionHost): PsiHostChainCallTypeHintsRegistry = synchronized(host) {
            val stored = host.getUserData(psiHostChainHintsRegistry)
            if (stored == null) {
                host.putUserData(psiHostChainHintsRegistry, PsiHostChainCallTypeHintsRegistry())
                host.getUserData(psiHostChainHintsRegistry)!!
            } else stored
        }
    }
}